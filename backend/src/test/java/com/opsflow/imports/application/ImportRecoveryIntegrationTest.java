/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.imports.domain.StoredFile;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.application.MetricCommandService;
import com.opsflow.reporting.application.PeriodCommandService;
import com.opsflow.submission.ImportAuthorizationMode;
import com.opsflow.support.ImportTestConfig;
import com.opsflow.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What a process death leaves behind, and what start-up does about it. Nothing is resumed from a
 * half-applied chunk; the user is told the truth and re-uploads.
 */
@IntegrationTest
@Import(ImportTestConfig.class)
class ImportRecoveryIntegrationTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final String CSV = "organization_code,metric_code,value,note\nALPHA,HEADCOUNT,42,\n";

    @Autowired
    private ImportJobRecoveryService recovery;

    @Autowired
    private ImportJobRepository jobs;

    @Autowired
    private ImportFileStore files;

    @Autowired
    private OrganizationCommandService organizationCommands;

    @Autowired
    private MetricCommandService metricCommands;

    @Autowired
    private PeriodCommandService periodCommands;

    @Autowired
    private JdbcTemplate jdbc;

    private OrganizationId alpha;
    private PeriodId period;
    private UserId member;

    @BeforeEach
    void setUp() {
        jdbc.execute(
                """
                TRUNCATE opsflow.import_row_error, opsflow.import_job,
                         opsflow.submission_transition, opsflow.submission_value, opsflow.submission,
                         opsflow.reporting_period_metric, opsflow.reporting_period_organization,
                         opsflow.reporting_period, opsflow.metric_definition,
                         opsflow.org_membership, opsflow.organization, opsflow.app_user CASCADE
                """);

        member = user("ria");
        alpha = organizationCommands
                .create(new OrganizationCode("ALPHA"), "Alpha unit")
                .id();
        organizationCommands.addMember(alpha, member);

        MetricId headcount = metricCommands
                .create(
                        new MetricCode("HEADCOUNT"),
                        "Headcount",
                        null,
                        "people",
                        ValueType.INTEGER,
                        new NumericRange(BigDecimal.ZERO, new BigDecimal("1000")))
                .id();
        period = openPeriod(headcount);
    }

    @Test
    void anImportLeftRunningByADeadProcessIsFailedWithItsLastTruthfulCounts() {
        ImportJobId id = persist(ImportStatus.RUNNING, 120, 100, 20);

        recovery.recover();

        ImportJob recovered = jobs.findById(id).orElseThrow();
        assertThat(recovered.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(recovered.failure()).contains(ImportFailure.INTERRUPTED_BY_SHUTDOWN);
        assertThat(recovered.processedRows()).isEqualTo(120);
        assertThat(recovered.validRows()).isEqualTo(100);
        assertThat(recovered.finishedAt()).isPresent();
    }

    @Test
    void anImportThatNeverStartedIsRunAfterTheRestart() {
        ImportJobId id = persist(ImportStatus.PENDING, 0, 0, 0);

        recovery.recover();

        await().atMost(Duration.ofSeconds(20))
                .until(() -> jobs.findById(id).orElseThrow().status() == ImportStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.submission_value", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void aFinishedImportIsLeftAlone() {
        ImportJobId id = persist(ImportStatus.COMPLETED, 10, 10, 0);

        recovery.recover();

        assertThat(jobs.findById(id).orElseThrow().status()).isEqualTo(ImportStatus.COMPLETED);
    }

    private ImportJobId persist(ImportStatus status, int processed, int valid, int invalid) {
        ImportJobId id = ImportJobId.generate(CLOCK);
        StoredFile stored = files.store(id, new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));
        jobs.insert(ImportJob.builder()
                .id(id)
                .reportingPeriodId(period)
                .organizationId(alpha)
                .initiatedBy(member)
                .authorizationMode(ImportAuthorizationMode.ORGANIZATION_MEMBER)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFingerprint(stored.checksum())
                .originalFilename("values.csv")
                .storageRef(stored.ref())
                .fileSizeBytes(stored.sizeBytes())
                .fileChecksum(stored.checksum())
                .submittedRows(stored.lineCount())
                .build());

        if (status != ImportStatus.PENDING) {
            ImportJob job = jobs.findById(id).orElseThrow();
            job.start(Instant.now(CLOCK));
            job.recordProgress(processed, valid, invalid);
            if (status == ImportStatus.COMPLETED) {
                job.complete(Instant.now(CLOCK));
            }
            jobs.save(job);
        }
        return id;
    }

    private UserId user(String username) {
        UserId id = UserId.generate(CLOCK);
        jdbc.update(
                """
                INSERT INTO opsflow.app_user
                       (id, oidc_issuer, oidc_subject, username, display_name, first_seen_at, last_seen_at)
                VALUES (?, 'https://issuer.example/realms/opsflow', ?, ?, ?, now(), now())
                """,
                id.value(),
                username + "-subject",
                username,
                username + " Person");
        return id;
    }

    private PeriodId openPeriod(MetricId metric) {
        var created = periodCommands.create(
                new PeriodCode("2026-Q1"),
                PeriodType.QUARTER,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 15));
        long version = periodCommands
                .replaceMetricScope(
                        created.id(),
                        List.of(new PeriodCommandService.MetricScopeEntry(metric, false, 0)),
                        created.version())
                .version();
        version = periodCommands
                .replaceParticipants(created.id(), new LinkedHashSet<>(Set.of(alpha)), version)
                .version();
        periodCommands.open(created.id(), version);
        return created.id();
    }
}
