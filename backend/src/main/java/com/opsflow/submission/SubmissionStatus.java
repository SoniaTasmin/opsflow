/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * REJECTED is its own editable state rather than a return to DRAFT: the reviewer's reason has to
 * outlive the transition, and the history has to show it was returned rather than never finished.
 */
public enum SubmissionStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }

    public boolean terminal() {
        return this == APPROVED;
    }
}
