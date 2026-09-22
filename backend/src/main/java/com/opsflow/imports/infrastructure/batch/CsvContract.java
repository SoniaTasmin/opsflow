/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import java.util.List;

/**
 * {@code organization_code,metric_code,value,note}.
 *
 * <p>The period comes from the request, not the file: a period column would let one upload write
 * across periods the request was never authorized for.
 */
public final class CsvContract {

    public static final List<String> COLUMNS = List.of("organization_code", "metric_code", "value", "note");
    public static final String HEADER = String.join(",", COLUMNS);

    private CsvContract() {}

    public static boolean matches(String headerLine) {
        if (headerLine == null) {
            return false;
        }
        String normalised = headerLine.replace("﻿", "").replace("\r", "").trim();
        return normalised.equalsIgnoreCase(HEADER);
    }
}
