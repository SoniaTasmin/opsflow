/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import com.opsflow.identity.UserSummary;
import com.opsflow.submission.application.SubmissionDetail;
import com.opsflow.submission.domain.SubmissionView;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record SubmissionDetailResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID organizationId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String organizationCode,
        @Schema(requiredMode = RequiredMode.REQUIRED) String organizationName,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID reportingPeriodId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String periodCode,
        @Schema(requiredMode = RequiredMode.REQUIRED) String periodStatus,
        @Schema(requiredMode = RequiredMode.REQUIRED) LocalDate submissionDeadline,
        @Schema(requiredMode = RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = RequiredMode.REQUIRED) boolean editable,
        String reviewerName,
        String submittedByName,
        String decidedByName,
        Instant submittedAt,
        Instant decidedAt,
        String rejectionReason,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<SubmissionMetricResponse> metrics,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<SubmissionValueResponse> values,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {

    public static SubmissionDetailResponse from(SubmissionDetail detail) {
        SubmissionView view = detail.submission();
        return new SubmissionDetailResponse(
                view.id().value(),
                view.organizationId().value(),
                detail.organization().code().value(),
                detail.organization().name(),
                view.reportingPeriodId().value(),
                detail.period().code().value(),
                detail.period().status().name(),
                detail.period().submissionDeadline(),
                view.status().name(),
                view.status().editable(),
                displayName(detail.reviewer()),
                displayName(detail.submittedBy()),
                displayName(detail.decidedBy()),
                view.submittedAt(),
                view.decidedAt(),
                view.rejectionReason(),
                detail.scope().stream().map(SubmissionMetricResponse::from).toList(),
                detail.values().stream().map(SubmissionValueResponse::from).toList(),
                view.version());
    }

    private static String displayName(Optional<UserSummary> user) {
        return user.map(UserSummary::displayName).orElse(null);
    }
}
