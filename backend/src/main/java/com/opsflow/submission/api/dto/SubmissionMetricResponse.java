/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import com.opsflow.reporting.ScopedMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;
import java.util.UUID;

/** A metric the submission is expected to report, carrying everything its input needs. */
public record SubmissionMetricResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID metricId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) String unit,
        @Schema(requiredMode = RequiredMode.REQUIRED) String valueType,
        BigDecimal minValue,
        BigDecimal maxValue,
        @Schema(requiredMode = RequiredMode.REQUIRED) boolean required,
        @Schema(requiredMode = RequiredMode.REQUIRED) int displayOrder) {

    public static SubmissionMetricResponse from(ScopedMetric metric) {
        return new SubmissionMetricResponse(
                metric.metricId().value(),
                metric.code().value(),
                metric.name(),
                metric.unit(),
                metric.valueType().name(),
                metric.acceptedRange().min(),
                metric.acceptedRange().max(),
                metric.required(),
                metric.displayOrder());
    }
}
