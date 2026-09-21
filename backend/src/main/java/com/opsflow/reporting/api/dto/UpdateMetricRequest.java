/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Code and value type are not updatable: stored values were validated against them. */
public record UpdateMetricRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 32) String unit,
        BigDecimal minValue,
        BigDecimal maxValue,
        @PositiveOrZero long version) {}
