/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.opsflow.identity.CurrentUserProvider;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.security.JwtRoleConverter;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.application.MetricCommandService;
import com.opsflow.reporting.application.PeriodCommandService;
import com.opsflow.support.ImportTestConfig;
import com.opsflow.support.InMemoryImportFileStore;
import com.opsflow.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Two identical uploads arriving together must produce one import, not two. The unique index is
 * what decides it; this proves the losing request resolves to the winner's job rather than
 * reporting a conflict or leaving its file behind.
 */
@IntegrationTest
@Import(ImportTestConfig.class)
class ImportAdmissionConcurrencyIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";
    private static final String CSV = "organization_code,metric_code,value,note\nALPHA,HEADCOUNT,42,\n";
    private static final String OTHER_CSV = "organization_code,metric_code,value,note\nALPHA,HEADCOUNT,43,\n";

    @Autowired
    private UploadImportService uploads;

    @Autowired
    private ImportFileStore files;

    @Autowired
    private CurrentUserProvider currentUser;

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
        ((InMemoryImportFileStore) files).clear();

        alpha = organizationCommands
                .create(new OrganizationCode("ALPHA"), "Alpha unit")
                .id();
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

        // Provisions the local user, which admission needs before either thread runs.
        asReporter(() -> currentUser.currentUserId().orElseThrow());
        organizationCommands.addMember(
                alpha, asReporter(() -> currentUser.currentUserId().orElseThrow()));
    }

    @Test
    void theDatabaseIsWhatMakesIdempotencyRaceSafe() {
        List<Map<String, Object>> indexes = jdbc.queryForList(
                """
                SELECT indexdef FROM pg_indexes
                 WHERE schemaname = 'opsflow' AND tablename = 'import_job' AND indexname = 'ux_import_idempotency'
                """);

        assertThat(indexes).singleElement().satisfies(index -> assertThat(String.valueOf(index.get("indexdef")))
                .contains("UNIQUE")
                .contains("created_by")
                .contains("idempotency_key"));
    }

    @Test
    void twoIdenticalUploadsWithOneKeyProduceOneImport() throws Exception {
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = inParallel(() -> uploadAsReporter(CSV, key), () -> uploadAsReporter(CSV, key));

        List<UploadOutcome> accepted = outcomes.stream()
                .filter(UploadOutcome.class::isInstance)
                .map(UploadOutcome.class::cast)
                .toList();
        assertThat(accepted).hasSize(2);
        assertThat(accepted.stream().map(outcome -> outcome.job().id()).distinct())
                .as("both callers resolve to the same job")
                .hasSize(1);
        assertThat(accepted.stream().filter(UploadOutcome::created).count())
                .as("only one of them created it")
                .isEqualTo(1L);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.import_job", Long.class))
                .isEqualTo(1L);
        // The loser streamed a file before discovering the race; it must not be left behind.
        assertThat(((InMemoryImportFileStore) files).size()).isEqualTo(1);

        ImportJobId job = accepted.getFirst().job().id();
        awaitTerminal(job);
        assertThat(batchExecutionsFor(job)).isEqualTo(1L);
    }

    @Test
    void twoConflictingUploadsWithOneKeyLeaveOneImportAndOneConflict() throws Exception {
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = inParallel(() -> uploadAsReporter(CSV, key), () -> uploadAsReporter(OTHER_CSV, key));

        assertThat(outcomes).filteredOn(UploadOutcome.class::isInstance).hasSize(1);
        assertThat(outcomes)
                .filteredOn(ConflictException.class::isInstance)
                .as("the request that did not win is a conflict, not a second import")
                .hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.import_job", Long.class))
                .isEqualTo(1L);
        assertThat(((InMemoryImportFileStore) files).size()).isEqualTo(1);
    }

    private UploadOutcome uploadAsReporter(String csv, String key) {
        return asReporter(() -> uploads.upload(new UploadImportCommand(
                period,
                alpha,
                key,
                "values.csv",
                "text/csv",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)))));
    }

    /** Admission reads the security context, so each thread establishes its own. */
    private <T> T asReporter(Callable<T> work) {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("reporter-subject")
                .claim("preferred_username", "ria")
                .claim("name", "Ria Reporter")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .audience(List.of("opsflow-api"))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        token, new JwtRoleConverter().convert(token).getAuthorities()));
        try {
            return work.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void awaitTerminal(ImportJobId job) {
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            String status = jdbc.queryForObject(
                    "SELECT status FROM opsflow.import_job WHERE id = ?", String.class, job.value());
            return "COMPLETED".equals(status) || "FAILED".equals(status);
        });
    }

    private long batchExecutionsFor(ImportJobId job) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM batch.BATCH_JOB_EXECUTION_PARAMS
                 WHERE PARAMETER_NAME = 'importJobId' AND PARAMETER_VALUE = ?
                """,
                Long.class,
                job.toString());
    }

    private List<Object> inParallel(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures =
                    List.of(pool.submit(() -> attempt(barrier, first)), pool.submit(() -> attempt(barrier, second)));
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private static Object attempt(CyclicBarrier barrier, Callable<?> work) throws Exception {
        barrier.await();
        try {
            return work.call();
        } catch (RuntimeException ex) {
            return ex;
        }
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
