/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.imports.application.ImportJobParameters;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.imports.domain.ImportJobView;
import com.opsflow.imports.domain.ImportRowError;
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
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The import path end to end against a real database: a file goes in, submission values come out,
 * and every rule the submission module owns still applies.
 */
@IntegrationTest
@Import(ImportTestConfig.class)
class CsvImportJobIntegrationTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final String HEADER = "organization_code,metric_code,value,note\n";

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job csvImportJob;

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
    private OrganizationId beta;
    private OrganizationId outsider;
    private PeriodId period;
    private UserId member;
    private UserId administrator;

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
        administrator = user("ada");

        alpha = organizationCommands
                .create(new OrganizationCode("ALPHA"), "Alpha unit")
                .id();
        beta = organizationCommands
                .create(new OrganizationCode("BETA"), "Beta unit")
                .id();
        outsider = organizationCommands
                .create(new OrganizationCode("GAMMA"), "Gamma unit")
                .id();
        organizationCommands.addMember(alpha, member);
        organizationCommands.addMember(beta, member);

        MetricId headcount =
                metric("HEADCOUNT", ValueType.INTEGER, new NumericRange(BigDecimal.ZERO, new BigDecimal("1000")));
        MetricId share = metric("SHARE", ValueType.PERCENTAGE, NumericRange.UNBOUNDED);
        period = openPeriod(List.of(headcount, share), Set.of(alpha, beta));
    }

    // ------------------------------------------------------------ the happy path

    @Test
    void appliesValuesThroughTheSubmissionAggregate() {
        ImportJobId job = run(
                HEADER + "ALPHA,HEADCOUNT,42,solid quarter\nBETA,HEADCOUNT,7,\n",
                null,
                member,
                ImportAuthorizationMode.ORGANIZATION_MEMBER);

        ImportJobView view = jobs.viewById(job).orElseThrow();
        assertThat(view.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(view.validRows()).isEqualTo(2);
        assertThat(view.invalidRows()).isZero();
        assertThat(view.processedRows()).isEqualTo(2);

        assertThat(valueOf(alpha, "HEADCOUNT")).isEqualByComparingTo("42");
        assertThat(valueOf(beta, "HEADCOUNT")).isEqualByComparingTo("7");
    }

    @Test
    void importedValuesAreMarkedAsImportedAndAttributedToTheInitiator() {
        run(HEADER + "ALPHA,HEADCOUNT,42,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        Map<String, Object> row = jdbc.queryForMap(
                """
                SELECT v.source, v.created_by, v.note
                  FROM opsflow.submission_value v
                  JOIN opsflow.submission s ON s.id = v.submission_id
                 WHERE s.organization_id = ?
                """,
                alpha.value());

        assertThat(row.get("source")).isEqualTo("IMPORT");
        assertThat(row.get("created_by")).isEqualTo(member.value());
    }

    @Test
    void anImportCreatesTheSubmissionItNeedsAsADraft() {
        run(HEADER + "ALPHA,HEADCOUNT,42,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM opsflow.submission WHERE organization_id = ?", String.class, alpha.value()))
                .isEqualTo("DRAFT");
        // Creation is a status change and is recorded; applying values is not.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.submission_transition", Long.class))
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------ authority

    /**
     * The job is admitted as a member but the initiator belongs to nothing, which can only be
     * caught while processing: admission is not re-run on a batch thread.
     */
    @Test
    void membershipIsRecheckedWhileProcessing() {
        UserId stranger = user("sam");

        ImportJobId job =
                run(HEADER + "ALPHA,HEADCOUNT,42,\n", null, stranger, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("ORG_NOT_PERMITTED"));
        assertThat(submissionCount()).isZero();
    }

    @Test
    void anAdminOverrideWritesForOrganizationsTheInitiatorDoesNotBelongTo() {
        ImportJobId job =
                run(HEADER + "ALPHA,HEADCOUNT,42,\n", null, administrator, ImportAuthorizationMode.ADMIN_OVERRIDE);

        assertThat(jobs.viewById(job).orElseThrow().validRows()).isEqualTo(1);
        assertThat(valueOf(alpha, "HEADCOUNT")).isEqualByComparingTo("42");
    }

    @Test
    void anAdminOverrideWaivesMembershipAndNothingElse() {
        ImportJobId job =
                run(HEADER + "GAMMA,HEADCOUNT,42,\n", null, administrator, ImportAuthorizationMode.ADMIN_OVERRIDE);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("ORG_NOT_IN_PERIOD_SCOPE"));
        assertThat(submissionCount()).isZero();
    }

    // ------------------------------------------------------------ submission rules

    @Test
    void anApprovedSubmissionIsLeftExactlyAsItWas() {
        run(HEADER + "ALPHA,HEADCOUNT,42,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);
        approveAlphaSubmission();

        ImportJobId job = run(
                HEADER + "ALPHA,HEADCOUNT,99,\nALPHA,SHARE,10,\n",
                null,
                member,
                ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(valueOf(alpha, "HEADCOUNT")).isEqualByComparingTo("42");
        ImportJobView view = jobs.viewById(job).orElseThrow();
        assertThat(view.invalidRows()).isEqualTo(2);
        assertThat(errorsOf(job)).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("SUBMISSION_NOT_EDITABLE");
            assertThat(error.affectedRows()).isEqualTo(2);
        });
    }

    @Test
    void aRejectedSubmissionCanStillBeCorrectedByAnImport() {
        run(HEADER + "ALPHA,HEADCOUNT,42,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);
        rejectAlphaSubmission();

        run(HEADER + "ALPHA,HEADCOUNT,44,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(valueOf(alpha, "HEADCOUNT")).isEqualByComparingTo("44");
    }

    @Test
    void aValueOutsideTheMetricsRangeIsRejected() {
        ImportJobId job =
                run(HEADER + "ALPHA,HEADCOUNT,5000,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("VALUE_OUT_OF_RANGE"));
    }

    /** The metric declares no maximum; the type supplies one, and the import honours it. */
    @Test
    void aPercentageAboveOneHundredIsRejected() {
        ImportJobId job = run(HEADER + "ALPHA,SHARE,150,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("VALUE_OUT_OF_RANGE"));
    }

    // ------------------------------------------------------------ the file itself

    @Test
    void aWrongHeaderStopsTheImportBeforeAnyRowIsTouched() {
        ImportJobId job = run(
                "org,metric,value\nALPHA,HEADCOUNT,42\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        ImportJobView view = jobs.viewById(job).orElseThrow();
        assertThat(view.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(view.failure()).isEqualTo(ImportFailure.HEADER_MISMATCH);
        assertThat(submissionCount()).isZero();
    }

    @Test
    void anUnknownOrganizationCodeIsReportedAgainstItsRow() {
        ImportJobId job =
                run(HEADER + "NOWHERE,HEADCOUNT,42,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("ORG_UNKNOWN");
            assertThat(error.rowNumber()).isEqualTo(2);
        });
    }

    @Test
    void aMetricThePeriodDoesNotCollectIsReportedAgainstItsRow() {
        metric("ABSENCE", ValueType.DECIMAL, NumericRange.UNBOUNDED);

        ImportJobId job = run(HEADER + "ALPHA,ABSENCE,3,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("METRIC_UNKNOWN"));
    }

    @Test
    void aValueThatIsNotANumberIsReportedAgainstItsRow() {
        ImportJobId job =
                run(HEADER + "ALPHA,HEADCOUNT,many,\n", null, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("VALUE_NOT_NUMERIC"));
    }

    @Test
    void theLastValueForAMetricWinsAndTheEarlierOneIsReported() {
        ImportJobId job = run(
                HEADER + "ALPHA,HEADCOUNT,10,\nALPHA,HEADCOUNT,20,\n",
                null,
                member,
                ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(valueOf(alpha, "HEADCOUNT")).isEqualByComparingTo("20");
        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("DUPLICATE_ROW"));
    }

    @Test
    void goodRowsAreAppliedEvenWhenOthersFail() {
        ImportJobId job = run(
                HEADER + "ALPHA,HEADCOUNT,42,\nNOWHERE,HEADCOUNT,1,\nBETA,HEADCOUNT,8,\nALPHA,HEADCOUNT,notanumber,\n",
                null,
                member,
                ImportAuthorizationMode.ORGANIZATION_MEMBER);

        ImportJobView view = jobs.viewById(job).orElseThrow();
        assertThat(view.validRows()).isEqualTo(2);
        assertThat(view.invalidRows()).isEqualTo(2);
        assertThat(valueOf(alpha, "HEADCOUNT")).isEqualByComparingTo("42");
        assertThat(valueOf(beta, "HEADCOUNT")).isEqualByComparingTo("8");
    }

    @Test
    void aFileScopedToOneOrganizationRefusesRowsForAnother() {
        ImportJobId job =
                run(HEADER + "BETA,HEADCOUNT,7,\n", alpha, member, ImportAuthorizationMode.ORGANIZATION_MEMBER);

        assertThat(errorsOf(job)).singleElement().satisfies(error -> assertThat(error.code())
                .isEqualTo("ORG_OUT_OF_SCOPE"));
    }

    // ------------------------------------------------------------ helpers

    private ImportJobId run(String csv, OrganizationId scope, UserId initiator, ImportAuthorizationMode mode) {
        ImportJobId jobId = ImportJobId.generate(CLOCK);
        StoredFile stored = files.store(jobId, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        jobs.insert(ImportJob.builder()
                .id(jobId)
                .reportingPeriodId(period)
                .organizationId(scope)
                .initiatedBy(initiator)
                .authorizationMode(mode)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFingerprint(stored.checksum())
                .originalFilename("values.csv")
                .storageRef(stored.ref())
                .fileSizeBytes(stored.sizeBytes())
                .fileChecksum(stored.checksum())
                .submittedRows(stored.lineCount())
                .build());

        ImportJob job = jobs.findById(jobId).orElseThrow();
        job.start(java.time.Instant.now(CLOCK));
        jobs.save(job);

        try {
            var execution = jobOperator.start(
                    csvImportJob,
                    new JobParametersBuilder()
                            .addString(ImportJobParameters.IMPORT_JOB_ID, jobId.toString())
                            .addString(
                                    ImportJobParameters.CORRELATION_ID,
                                    UUID.randomUUID().toString())
                            .toJobParameters());
            assertThat(execution).isNotNull();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return jobId;
    }

    private List<ImportRowError> errorsOf(ImportJobId job) {
        return jobs.errorsOf(job, 100);
    }

    private BigDecimal valueOf(OrganizationId organizationId, String metricCode) {
        return jdbc.queryForObject(
                """
                SELECT v.value
                  FROM opsflow.submission_value v
                  JOIN opsflow.submission s ON s.id = v.submission_id
                  JOIN opsflow.metric_definition m ON m.id = v.metric_id
                 WHERE s.organization_id = ? AND m.code = ?
                """,
                BigDecimal.class,
                organizationId.value(),
                metricCode);
    }

    private long submissionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM opsflow.submission", Long.class);
    }

    private void approveAlphaSubmission() {
        jdbc.update(
                """
                UPDATE opsflow.submission
                   SET status = 'APPROVED', submitted_at = now(), submitted_by = ?,
                       decided_at = now(), decided_by = ?, version = version + 1
                 WHERE organization_id = ?
                """,
                member.value(),
                administrator.value(),
                alpha.value());
    }

    private void rejectAlphaSubmission() {
        jdbc.update(
                """
                UPDATE opsflow.submission
                   SET status = 'REJECTED', submitted_at = now(), submitted_by = ?,
                       decided_at = now(), decided_by = ?, rejection_reason = 'Check the payroll export',
                       version = version + 1
                 WHERE organization_id = ?
                """,
                member.value(),
                administrator.value(),
                alpha.value());
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

    private MetricId metric(String code, ValueType valueType, NumericRange range) {
        return metricCommands
                .create(new MetricCode(code), code + " metric", null, "unit", valueType, range)
                .id();
    }

    private PeriodId openPeriod(List<MetricId> metrics, Set<OrganizationId> participants) {
        var created = periodCommands.create(
                new PeriodCode("2026-Q1"),
                PeriodType.QUARTER,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 15));
        long version = periodCommands
                .replaceMetricScope(
                        created.id(),
                        metrics.stream()
                                .map(id -> new PeriodCommandService.MetricScopeEntry(id, false, 0))
                                .toList(),
                        created.version())
                .version();
        version = periodCommands
                .replaceParticipants(created.id(), new LinkedHashSet<>(participants), version)
                .version();
        periodCommands.open(created.id(), version);
        return created.id();
    }
}
