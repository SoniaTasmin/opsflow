/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ScopedMetricTest {

    private static ScopedMetric metric(ValueType valueType, NumericRange range) {
        return new ScopedMetric(
                MetricId.generate(Clock.systemUTC()), new MetricCode("SHARE"), "Share", "%", valueType, range, true, 0);
    }

    @Test
    void aPercentageIsBoundedEvenWhenTheMetricDeclaresNoLimits() {
        NumericRange accepted =
                metric(ValueType.PERCENTAGE, NumericRange.UNBOUNDED).acceptedRange();

        assertThat(accepted.contains(new BigDecimal("150"))).isFalse();
        assertThat(accepted.contains(new BigDecimal("-1"))).isFalse();
        assertThat(accepted.contains(new BigDecimal("37.5"))).isTrue();
    }

    @Test
    void aDeclaredRangeNarrowsTheTypesOwnBounds() {
        NumericRange accepted = metric(ValueType.PERCENTAGE, new NumericRange(new BigDecimal("10"), null))
                .acceptedRange();

        assertThat(accepted.minimum()).contains(new BigDecimal("10"));
        assertThat(accepted.maximum()).contains(new BigDecimal("100"));
    }

    @Test
    void anUntypedRangeIsLeftAlone() {
        NumericRange declared = new NumericRange(BigDecimal.ZERO, new BigDecimal("5000"));

        assertThat(metric(ValueType.DECIMAL, declared).acceptedRange()).isEqualTo(declared);
    }
}
