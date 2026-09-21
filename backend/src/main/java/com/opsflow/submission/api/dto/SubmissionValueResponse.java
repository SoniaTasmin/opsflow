/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import com.opsflow.submission.domain.SubmissionValue;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.math.BigDecimal;
import java.util.UUID;

public record SubmissionValueResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID metricId,
        @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal value,
        String note,
        @Schema(requiredMode = RequiredMode.REQUIRED) String source) {

    public static SubmissionValueResponse from(SubmissionValue value) {
        return new SubmissionValueResponse(
                value.metricId().value(),
                value.value(),
                value.note(),
                value.source().name());
    }
}
