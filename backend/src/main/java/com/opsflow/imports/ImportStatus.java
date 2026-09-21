/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports;

public enum ImportStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED;
    }
}
