/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import java.math.BigDecimal;

public enum ValueType {
    INTEGER,
    DECIMAL,
    PERCENTAGE;

    public boolean accepts(BigDecimal value) {
        if (value == null) {
            return false;
        }
        return this != INTEGER || value.stripTrailingZeros().scale() <= 0;
    }
}
