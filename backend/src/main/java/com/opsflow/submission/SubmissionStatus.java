/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

/**
 * The submission lifecycle.
 *
 * <pre>
 *   DRAFT ──submit──▶ SUBMITTED ──claim──▶ UNDER_REVIEW ──approve──▶ APPROVED
 *     ▲                   │                     │
 *     └────withdraw───────┘     ┌───release──────┤
 *                               ▼                ▼
 *                          SUBMITTED         REJECTED ──submit──▶ SUBMITTED
 * </pre>
 *
 * <p>REJECTED is a persisted editable state rather than a return to DRAFT: the reason a
 * reviewer gave has to survive long enough for the author to act on it, and the history has to
 * show that the submission was rejected rather than merely never finished.
 */
public enum SubmissionStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    /** Whether values may be added, changed or removed in this state. */
    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }

    public boolean terminal() {
        return this == APPROVED;
    }
}
