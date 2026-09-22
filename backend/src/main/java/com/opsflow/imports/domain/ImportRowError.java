/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

/**
 * @param rowNumber the first affected line, 1-based and counting the header
 * @param affectedRows above 1 when one rule invalidated a whole organization's rows
 */
public record ImportRowError(int rowNumber, String code, String message, int affectedRows) {

    public static ImportRowError row(int rowNumber, String code, String message) {
        return new ImportRowError(rowNumber, code, message, 1);
    }
}
