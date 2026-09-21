/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.ValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateMetricRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,40}$", message = "must be 2-40 characters of A-Z, 0-9 or underscore")
                String code,
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 32) String unit,
        @NotNull ValueType valueType,
        BigDecimal minValue,
        BigDecimal maxValue) {}
