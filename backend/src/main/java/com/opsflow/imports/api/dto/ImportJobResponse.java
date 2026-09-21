/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.api.dto;

import com.opsflow.imports.domain.ImportJobView;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

public record ImportJobResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID reportingPeriodId,
        UUID organizationId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = RequiredMode.REQUIRED) String originalFilename,
        @Schema(requiredMode = RequiredMode.REQUIRED) long fileSizeBytes,
        @Schema(requiredMode = RequiredMode.REQUIRED) int submittedRows,
        @Schema(requiredMode = RequiredMode.REQUIRED) int processedRows,
        @Schema(requiredMode = RequiredMode.REQUIRED) int validRows,
        @Schema(requiredMode = RequiredMode.REQUIRED) int invalidRows,
        String failureCode,
        String failureMessage,
        Instant startedAt,
        Instant finishedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt) {

    public static ImportJobResponse from(ImportJobView view) {
        return new ImportJobResponse(
                view.id().value(),
                view.reportingPeriodId().value(),
                view.organizationId() == null ? null : view.organizationId().value(),
                view.status().name(),
                view.originalFilename(),
                view.fileSizeBytes(),
                view.submittedRows(),
                view.processedRows(),
                view.validRows(),
                view.invalidRows(),
                view.failure() == null ? null : view.failure().name(),
                view.failure() == null ? null : view.failure().message(),
                view.startedAt(),
                view.finishedAt(),
                view.createdAt());
    }
}
