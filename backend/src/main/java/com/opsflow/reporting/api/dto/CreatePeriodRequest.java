/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.PeriodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record CreatePeriodRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{4,20}$", message = "must be 4-20 characters of A-Z, 0-9 or hyphen")
                String code,
        @NotNull PeriodType periodType,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull LocalDate submissionDeadline) {}
