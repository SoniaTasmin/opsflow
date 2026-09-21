/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.api.dto;

import com.opsflow.imports.domain.ImportRowError;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

public record ImportRowErrorResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) int rowNumber,
        @Schema(requiredMode = RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = RequiredMode.REQUIRED) int affectedRows) {

    public static ImportRowErrorResponse from(ImportRowError error) {
        return new ImportRowErrorResponse(error.rowNumber(), error.code(), error.message(), error.affectedRows());
    }
}
