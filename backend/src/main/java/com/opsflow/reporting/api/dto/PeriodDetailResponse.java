/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

public record PeriodDetailResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) PeriodResponse period,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ScopedMetricResponse> metrics,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ParticipantResponse> organizations) {}
