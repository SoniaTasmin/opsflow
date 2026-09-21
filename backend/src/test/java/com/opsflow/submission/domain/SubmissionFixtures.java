/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.reporting.ValueType;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.ValueSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

final class SubmissionFixtures {

    static final Clock CLOCK = Clock.systemUTC();
    static final Instant NOW = Instant.parse("2026-04-01T10:00:00Z");
    static final MetricId HEADCOUNT = MetricId.generate(CLOCK);
    static final MetricId REVENUE = MetricId.generate(CLOCK);
    static final MetricId SHARE = MetricId.generate(CLOCK);
    static final UserId AUTHOR = UserId.generate(CLOCK);
    static final UserId REVIEWER = UserId.generate(CLOCK);

    private SubmissionFixtures() {}

    static SubmissionValidationPolicy policy() {
        return new SubmissionValidationPolicy(List.of(
                new ScopedMetric(
                        HEADCOUNT,
                        new MetricCode("HEADCOUNT"),
                        "Headcount",
                        "people",
                        ValueType.INTEGER,
                        new NumericRange(BigDecimal.ZERO, new BigDecimal("10000")),
                        true,
                        0),
                new ScopedMetric(
                        REVENUE,
                        new MetricCode("REVENUE"),
                        "Revenue",
                        "EUR",
                        ValueType.DECIMAL,
                        NumericRange.UNBOUNDED,
                        false,
                        1),
                new ScopedMetric(
                        SHARE,
                        new MetricCode("SHARE"),
                        "Market share",
                        "%",
                        ValueType.PERCENTAGE,
                        NumericRange.UNBOUNDED,
                        false,
                        2)));
    }

    static Submission draft() {
        return Submission.create(
                SubmissionId.generate(CLOCK), OrganizationId.generate(CLOCK), PeriodId.generate(CLOCK), AUTHOR, NOW);
    }

    static Submission draftWithRequiredValues() {
        Submission submission = draft();
        submission.setValue(HEADCOUNT, new BigDecimal("42"), null, ValueSource.MANUAL, policy());
        return submission;
    }

    static Submission submitted() {
        Submission submission = draftWithRequiredValues();
        submission.submit(policy(), AUTHOR, NOW);
        return submission;
    }

    static Submission underReview() {
        Submission submission = submitted();
        submission.claim(REVIEWER, NOW);
        return submission;
    }

    static Submission approved() {
        Submission submission = underReview();
        submission.approve(REVIEWER, NOW, false);
        return submission;
    }

    static Submission rejected() {
        Submission submission = underReview();
        submission.reject(REVIEWER, "The headcount does not match the payroll export", NOW);
        return submission;
    }

    static Submission inState(SubmissionStatus status) {
        return switch (status) {
            case DRAFT -> draftWithRequiredValues();
            case SUBMITTED -> submitted();
            case UNDER_REVIEW -> underReview();
            case APPROVED -> approved();
            case REJECTED -> rejected();
        };
    }
}
