/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.ValueType;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class MetricDefinitionTest {

    private static final Clock CLOCK = Clock.systemUTC();

    private static MetricDefinition headcount() {
        return MetricDefinition.create(
                MetricId.generate(CLOCK),
                new MetricCode("HEADCOUNT"),
                "Headcount",
                "People employed",
                "people",
                ValueType.INTEGER,
                NumericRange.UNBOUNDED);
    }

    @Test
    void aNewMetricIsActive() {
        assertThat(headcount().isActive()).isTrue();
    }

    @Test
    void aPercentageMetricIsBoundedToZeroHundredByDefault() {
        MetricDefinition uptime = MetricDefinition.create(
                MetricId.generate(CLOCK),
                new MetricCode("UPTIME_PCT"),
                "Uptime",
                null,
                "%",
                ValueType.PERCENTAGE,
                NumericRange.UNBOUNDED);

        assertThat(uptime.range().minimum()).contains(BigDecimal.ZERO);
        assertThat(uptime.range().maximum()).contains(BigDecimal.valueOf(100));
    }

    @Test
    void rejectsAPercentageRangeOutsideZeroHundred() {
        assertThatThrownBy(() -> MetricDefinition.create(
                        MetricId.generate(CLOCK),
                        new MetricCode("BAD_PCT"),
                        "Bad",
                        null,
                        "%",
                        ValueType.PERCENTAGE,
                        new NumericRange(BigDecimal.ZERO, BigDecimal.valueOf(150))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThatThrownBy(() -> new NumericRange(BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void boundsCannotChangeOnceTheMetricIsCollected() {
        MetricDefinition metric = headcount();

        assertThatThrownBy(() -> metric.changeRange(new NumericRange(BigDecimal.ZERO, BigDecimal.TEN), true))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("METRIC_IN_USE"));
    }

    @Test
    void boundsCanChangeWhileTheMetricIsOnlyInDraftPeriods() {
        MetricDefinition metric = headcount();

        metric.changeRange(new NumericRange(BigDecimal.ZERO, BigDecimal.valueOf(10_000)), false);

        assertThat(metric.range().maximum()).contains(BigDecimal.valueOf(10_000));
    }

    @Test
    void archivingTwiceIsAConflict() {
        MetricDefinition metric = headcount();
        metric.archive(false);

        assertThatThrownBy(() -> metric.archive(false)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotArchiveWhileAnOpenPeriodCollectsIt() {
        assertThatThrownBy(() -> headcount().archive(true))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("METRIC_IN_USE"));
    }

    @Test
    void anArchivedMetricCannotBeEdited() {
        MetricDefinition metric = headcount();
        metric.archive(false);

        assertThatThrownBy(() -> metric.describe("New", null, "people"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("METRIC_ARCHIVED"));
    }

    @Test
    void integerMetricsRejectFractionalValues() {
        assertThat(ValueType.INTEGER.accepts(new BigDecimal("142"))).isTrue();
        assertThat(ValueType.INTEGER.accepts(new BigDecimal("142.0"))).isTrue();
        assertThat(ValueType.INTEGER.accepts(new BigDecimal("142.5"))).isFalse();
        assertThat(ValueType.DECIMAL.accepts(new BigDecimal("142.5"))).isTrue();
    }

    @Test
    void rangeContainmentIsInclusive() {
        NumericRange range = new NumericRange(BigDecimal.ZERO, BigDecimal.valueOf(100));

        assertThat(range.contains(BigDecimal.ZERO)).isTrue();
        assertThat(range.contains(BigDecimal.valueOf(100))).isTrue();
        assertThat(range.contains(BigDecimal.valueOf(101))).isFalse();
        assertThat(NumericRange.UNBOUNDED.contains(BigDecimal.valueOf(-999))).isTrue();
    }
}
