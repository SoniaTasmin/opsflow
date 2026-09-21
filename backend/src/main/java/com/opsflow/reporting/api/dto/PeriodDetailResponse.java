/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import java.util.List;

public record PeriodDetailResponse(
        PeriodResponse period, List<ScopedMetricResponse> metrics, List<ParticipantResponse> organizations) {}
