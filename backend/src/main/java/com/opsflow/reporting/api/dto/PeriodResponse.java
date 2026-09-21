/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.domain.PeriodView;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = RequiredMode.REQUIRED) String periodType,
        @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate startsOn,
        @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate endsOn,
        @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate submissionDeadline,
        @Schema(requiredMode = RequiredMode.REQUIRED) String status,
        Instant openedAt,
        Instant closedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) int metricCount,
        @Schema(requiredMode = RequiredMode.REQUIRED) int participantCount,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {

    public static PeriodResponse from(PeriodView view) {
        return new PeriodResponse(
                view.id().value(),
                view.code().value(),
                view.periodType().name(),
                view.startsOn(),
                view.endsOn(),
                view.submissionDeadline(),
                view.status().name(),
                view.openedAt(),
                view.closedAt(),
                view.metricCount(),
                view.participantCount(),
                view.version());
    }
}
