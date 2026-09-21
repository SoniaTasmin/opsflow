/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.domain.MetricView;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;
import java.util.UUID;

public record MetricResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = RequiredMode.REQUIRED) String name,
        String description,
        @Schema(requiredMode = RequiredMode.REQUIRED) String unit,
        @Schema(requiredMode = RequiredMode.REQUIRED) String valueType,
        BigDecimal minValue,
        BigDecimal maxValue,
        @Schema(requiredMode = RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {

    public static MetricResponse from(MetricView view) {
        return new MetricResponse(
                view.id().value(),
                view.code().value(),
                view.name(),
                view.description(),
                view.unit(),
                view.valueType().name(),
                view.range().min(),
                view.range().max(),
                view.active(),
                view.version());
    }
}
