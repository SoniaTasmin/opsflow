/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import com.opsflow.platform.error.BusinessRuleException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public record NumericRange(BigDecimal min, BigDecimal max) {

    public static final NumericRange UNBOUNDED = new NumericRange(null, null);

    public NumericRange {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessRuleException(
                    "METRIC_RANGE_INVALID",
                    "Minimum must not exceed maximum",
                    Map.of("minValue", min.toString(), "maxValue", max.toString()));
        }
    }

    public boolean contains(BigDecimal value) {
        if (value == null) {
            return false;
        }
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    public Optional<BigDecimal> minimum() {
        return Optional.ofNullable(min);
    }

    public Optional<BigDecimal> maximum() {
        return Optional.ofNullable(max);
    }
}
