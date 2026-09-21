/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import com.opsflow.identity.UserSummary;
import com.opsflow.submission.application.TransitionEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;

public record TransitionResponse(
        String fromStatus,
        @Schema(requiredMode = RequiredMode.REQUIRED) String toStatus,
        String actorName,
        String reason,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant occurredAt) {

    public static TransitionResponse from(TransitionEntry entry) {
        var transition = entry.transition();
        return new TransitionResponse(
                transition.from() == null ? null : transition.from().name(),
                transition.to().name(),
                entry.actor().map(UserSummary::displayName).orElse(null),
                transition.reason(),
                transition.occurredAt());
    }
}
