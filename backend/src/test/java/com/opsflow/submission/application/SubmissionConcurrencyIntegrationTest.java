/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.application.OrganizationCommandService;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.application.MetricCommandService;
import com.opsflow.reporting.application.PeriodCommandService;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.ValueSource;
import com.opsflow.submission.domain.Submission;
import com.opsflow.submission.domain.SubmissionRepository;
import com.opsflow.submission.domain.SubmissionValidationPolicy;
import com.opsflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The behaviours an in-memory database would fake. Both run through the same repository the
 * application uses, on real connections, against real constraints.
 */
@IntegrationTest
class SubmissionConcurrencyIntegrationTest {

    private static final Clock CLOCK = Clock.systemUTC();

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private OrganizationCommandService organizationCommands;

    @Autowired
    private MetricCommandService metricCommands;

    @Autowired
    private PeriodCommandService periodCommands;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private OrganizationId organization;
    private PeriodId period;
    private MetricId headcount;
    private UserId author;

    @BeforeEach
    void setUp() {
        jdbc.execute(
                """
                TRUNCATE opsflow.submission_transition, opsflow.submission_value, opsflow.submission,
                         opsflow.reporting_period_metric, opsflow.reporting_period_organization,
                         opsflow.reporting_period, opsflow.metric_definition,
                         opsflow.org_membership, opsflow.organization, opsflow.app_user CASCADE
                """);

        author = UserId.generate(CLOCK);
        jdbc.update(
                """
                INSERT INTO opsflow.app_user
                       (id, oidc_issuer, oidc_subject, username, display_name, first_seen_at, last_seen_at)
                VALUES (?, 'https://issuer.example/realms/opsflow', 'author-subject', 'ria', 'ria Person', now(), now())
                """,
                author.value());

        organization = organizationCommands
                .create(new OrganizationCode("ALPHA"), "Alpha unit")
                .id();
        headcount = metricCommands
                .create(
                        new MetricCode("HEADCOUNT"),
                        "Headcount",
                        null,
                        "people",
                        ValueType.INTEGER,
                        NumericRange.UNBOUNDED)
                .id();

        var created = periodCommands.create(
                new PeriodCode("2026-Q1"),
                PeriodType.QUARTER,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 15));
        period = created.id();
        long version = periodCommands
                .replaceMetricScope(
                        period,
                        List.of(new PeriodCommandService.MetricScopeEntry(headcount, true, 0)),
                        created.version())
                .version();
        Set<OrganizationId> participants = new LinkedHashSet<>(Set.of(organization));
        version = periodCommands
                .replaceParticipants(period, participants, version)
                .version();
        periodCommands.open(period, version);
    }

    @Test
    void twoConcurrentCreatesLeaveExactlyOneSubmission() throws Exception {
        List<Object> outcomes = inParallel(
                () -> inTransaction(() -> submissions.insert(newSubmission(), author)),
                () -> inTransaction(() -> submissions.insert(newSubmission(), author)));

        assertThat(outcomes).filteredOn(Submission.class::isInstance).hasSize(1);
        assertThat(outcomes)
                .filteredOn(ConflictException.class::isInstance)
                .singleElement()
                .satisfies(failure ->
                        assertThat(((ConflictException) failure).errorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.submission", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void aStaleVersionLosesRatherThanOverwritingSilently() throws Exception {
        Submission stored = inTransaction(() -> submissions.insert(newSubmission(), author));

        List<Object> outcomes = inParallel(
                () -> inTransaction(() -> saveValue(stored.id(), "1")),
                () -> inTransaction(() -> saveValue(stored.id(), "2")));

        assertThat(outcomes).filteredOn(Submission.class::isInstance).hasSize(1);
        assertThat(outcomes)
                .filteredOn(ConflictException.class::isInstance)
                .singleElement()
                .satisfies(failure ->
                        assertThat(((ConflictException) failure).errorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM opsflow.submission_value", Long.class))
                .isEqualTo(1L);
    }

    private Submission saveValue(SubmissionId id, String value) {
        Submission submission = submissions.findById(id).orElseThrow();
        submission.setValue(headcount, new BigDecimal(value), null, ValueSource.MANUAL, policy());
        return submissions.update(submission, submission.version(), author);
    }

    private Submission newSubmission() {
        return Submission.create(SubmissionId.generate(CLOCK), organization, period, author, Instant.now(CLOCK));
    }

    private SubmissionValidationPolicy policy() {
        return new SubmissionValidationPolicy(List.of(new ScopedMetric(
                headcount,
                new MetricCode("HEADCOUNT"),
                "Headcount",
                "people",
                ValueType.INTEGER,
                NumericRange.UNBOUNDED,
                true,
                0)));
    }

    private <T> T inTransaction(Callable<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    /** Both callers are released by the same barrier so the race is real rather than incidental. */
    private List<Object> inParallel(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures =
                    List.of(pool.submit(() -> attempt(barrier, first)), pool.submit(() -> attempt(barrier, second)));
            return List.of(futures.get(0).get(), futures.get(1).get());
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
}
