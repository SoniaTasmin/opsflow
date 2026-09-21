/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import java.util.regex.Pattern;

public record PeriodCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9-]{4,20}$");

    public PeriodCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Period code must be 4-20 characters of A-Z, 0-9 or hyphen");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
