/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization;

import java.util.regex.Pattern;

/**
 * Immutable once assigned: it is the key CSV imports arrive with, so renaming one would
 * silently repoint every historical import that referenced it.
 */
public record OrganizationCode(String value) {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9_-]{2,32}$");

    public OrganizationCode {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Organization code must be 2-32 characters of A-Z, 0-9, underscore or hyphen");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
