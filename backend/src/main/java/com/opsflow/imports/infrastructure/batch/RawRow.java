/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

/** @param parseError set when the line could not be split into fields at all */
record RawRow(int rowNumber, String organizationCode, String metricCode, String value, String note, String parseError) {

    static RawRow malformed(int rowNumber, String reason) {
        return new RawRow(rowNumber, null, null, null, null, reason);
    }
}
