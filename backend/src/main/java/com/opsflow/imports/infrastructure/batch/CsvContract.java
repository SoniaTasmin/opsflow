/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import java.util.List;

/**
 * The import file format.
 *
 * <pre>
 * organization_code,metric_code,value,note
 * BERLIN_OPS,HEADCOUNT,42,optional note
 * </pre>
 *
 * <p>The reporting period comes from the request rather than the file: one upload targets one
 * period, and a period column would let a single file write across periods the request was never
 * authorized for.
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
