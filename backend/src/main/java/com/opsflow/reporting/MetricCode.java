/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import java.util.regex.Pattern;

/** Immutable once assigned: CSV imports reference it. */
public record MetricCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9_]{2,40}$");

    public MetricCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Metric code must be 2-40 characters of A-Z, 0-9 or underscore");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
