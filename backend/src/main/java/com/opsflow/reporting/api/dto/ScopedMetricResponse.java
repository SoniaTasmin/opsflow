/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.reporting.ScopedMetric;
import java.math.BigDecimal;
import java.util.UUID;

public record ScopedMetricResponse(
        UUID metricId,
        String code,
        String name,
        String unit,
        String valueType,
        BigDecimal minValue,
        BigDecimal maxValue,
        boolean required,
        int displayOrder) {

    public static ScopedMetricResponse from(ScopedMetric metric) {
        return new ScopedMetricResponse(
                metric.metricId().value(),
                metric.code().value(),
                metric.name(),
                metric.unit(),
                metric.valueType().name(),
                metric.range().min(),
                metric.range().max(),
                metric.required(),
                metric.displayOrder());
    }
}
