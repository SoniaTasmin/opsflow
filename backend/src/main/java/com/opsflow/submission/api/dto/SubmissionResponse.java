/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import com.opsflow.submission.application.SubmissionListItem;
import com.opsflow.submission.domain.SubmissionView;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID organizationId,
        String organizationCode,
        String organizationName,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID reportingPeriodId,
        String periodCode,
        String periodStatus,
        @Schema(requiredMode = RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = RequiredMode.REQUIRED) int valueCount,
        Instant submittedAt,
        Instant decidedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {

    public static SubmissionResponse from(SubmissionListItem item) {
        SubmissionView view = item.submission();
        return new SubmissionResponse(
                view.id().value(),
                view.organizationId().value(),
                item.organization() == null ? null : item.organization().code().value(),
                item.organization() == null ? null : item.organization().name(),
                view.reportingPeriodId().value(),
                item.period() == null ? null : item.period().code().value(),
                item.period() == null ? null : item.period().status().name(),
                view.status().name(),
                view.valueCount(),
                view.submittedAt(),
                view.decidedAt(),
                view.updatedAt(),
                view.version());
    }
}
