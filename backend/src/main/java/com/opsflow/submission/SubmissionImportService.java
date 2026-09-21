/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * The only way values may reach a submission from outside this module.
 *
 * <p>Applies them through the same aggregate and the same rules manual editing uses, so there is
 * no bulk path with weaker invariants. Callers group work to make it efficient; they never
 * bypass it.
 */
public interface SubmissionImportService {

    /**
     * Applies one organization's values. A rule that invalidates the whole group is reported in
     * the result rather than thrown, so a caller processing many groups can record it and carry
     * on; a concurrency conflict is thrown, because the right answer to it is to retry.
     */
    ApplyValuesResult applyValues(ApplyValuesCommand command);
}
