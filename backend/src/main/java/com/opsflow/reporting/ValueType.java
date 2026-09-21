/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import java.math.BigDecimal;

public enum ValueType {
    INTEGER,
    DECIMAL,
    PERCENTAGE;

    private static final NumericRange PERCENT = new NumericRange(BigDecimal.ZERO, new BigDecimal("100"));

    public boolean accepts(BigDecimal value) {
        if (value == null) {
            return false;
        }
        return this != INTEGER || value.stripTrailingZeros().scale() <= 0;
    }

    /**
     * Bounds the type carries regardless of what a metric declares. A percentage metric that
     * names no maximum still cannot report 150.
     */
    public NumericRange impliedRange() {
        return this == PERCENTAGE ? PERCENT : NumericRange.UNBOUNDED;
    }
}
