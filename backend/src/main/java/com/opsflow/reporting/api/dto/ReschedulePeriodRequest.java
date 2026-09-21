/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record ReschedulePeriodRequest(
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull LocalDate submissionDeadline,
        @PositiveOrZero long version) {}
