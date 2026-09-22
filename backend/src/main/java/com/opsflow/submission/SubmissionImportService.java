/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * The only way values reach a submission from outside this module, and they take the same
 * aggregate and rules as manual editing. Callers group work; they never bypass it (ADR-0006).
 */
public interface SubmissionImportService {

    /**
     * A rule invalidating the whole group is returned, not thrown, so a caller processing many
     * groups can record it and continue. A concurrency conflict is thrown: the answer is a retry.
     */
    ApplyValuesResult applyValues(ApplyValuesCommand command);
}
