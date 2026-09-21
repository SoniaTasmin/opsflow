/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import static com.opsflow.submission.domain.SubmissionFixtures.CLOCK;
import static com.opsflow.submission.domain.SubmissionFixtures.HEADCOUNT;
import static com.opsflow.submission.domain.SubmissionFixtures.REVENUE;
import static com.opsflow.submission.domain.SubmissionFixtures.SHARE;
import static com.opsflow.submission.domain.SubmissionFixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.reporting.MetricId;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SubmissionValidationPolicyTest {

    @Test
    void refusesAMetricThePeriodDoesNotCollect() {
        assertThatThrownBy(() -> policy().validate(MetricId.generate(CLOCK), BigDecimal.ONE))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("METRIC_NOT_IN_SCOPE");
    }

    @Test
    void refusesAValueOutsideTheMetricsRange() {
        assertThatThrownBy(() -> policy().validate(HEADCOUNT, new BigDecimal("10001")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("VALUE_OUT_OF_RANGE");
    }

    @Test
    void refusesAFractionalValueForAWholeNumberMetric() {
        assertThatThrownBy(() -> policy().validate(HEADCOUNT, new BigDecimal("42.5")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("VALUE_NOT_INTEGER");
    }

    // The column is numeric(18,4): accepting more would round the number silently.
    @Test
    void refusesMorePrecisionThanTheSchemaCanHold() {
        assertThatThrownBy(() -> policy().validate(REVENUE, new BigDecimal("1.234567")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("VALUE_SCALE_EXCEEDED");
    }

    // The metric declares no maximum; the type supplies one.
    @Test
    void refusesAPercentageAboveOneHundred() {
        assertThatThrownBy(() -> policy().validate(SHARE, new BigDecimal("150")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("VALUE_OUT_OF_RANGE");
    }

    @Test
    void acceptsAPercentageWithinBounds() {
        assertThatCode(() -> policy().validate(SHARE, new BigDecimal("37.5"))).doesNotThrowAnyException();
    }

    @Test
    void acceptsTrailingZeroesThatDoNotAddPrecision() {
        assertThatCode(() -> policy().validate(HEADCOUNT, new BigDecimal("42.000")))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsOnlyTheMetricsThatMustBeAnswered() {
        assertThat(policy().requiredMetrics()).containsExactly(HEADCOUNT);
    }
}
