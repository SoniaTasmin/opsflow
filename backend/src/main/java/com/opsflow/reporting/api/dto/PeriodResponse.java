/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.domain.PeriodView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodResponse(
        UUID id,
        String code,
        String periodType,
        LocalDate startsOn,
        LocalDate endsOn,
        LocalDate submissionDeadline,
        String status,
        Instant openedAt,
        Instant closedAt,
        int metricCount,
        int participantCount,
        long version) {

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
