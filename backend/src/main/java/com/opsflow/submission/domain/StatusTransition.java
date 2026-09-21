/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.submission.SubmissionStatus;
import java.time.Instant;

/**
 * One entry in a submission's workflow history.
 *
 * @param from null for the entry recording the submission's creation
 * @param actorId null only when a transition was made outside a request, which no current path does
 */
public record StatusTransition(
        SubmissionStatus from, SubmissionStatus to, UserId actorId, String reason, Instant occurredAt) {

    public StatusTransition {
        if (to == null) {
            throw new IllegalArgumentException("to is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        reason = reason == null || reason.isBlank() ? null : reason.strip();
    }
}
