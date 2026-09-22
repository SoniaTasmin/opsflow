/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.submission.SubmissionStatus;
import java.time.Instant;

/** @param from null for the entry recording the submission's creation */
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
