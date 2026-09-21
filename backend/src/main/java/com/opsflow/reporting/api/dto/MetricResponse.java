/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.domain.MetricView;
import java.math.BigDecimal;
import java.util.UUID;

public record MetricResponse(
        UUID id,
        String code,
        String name,
        String description,
        String unit,
        String valueType,
        BigDecimal minValue,
        BigDecimal maxValue,
        boolean active,
        long version) {

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
