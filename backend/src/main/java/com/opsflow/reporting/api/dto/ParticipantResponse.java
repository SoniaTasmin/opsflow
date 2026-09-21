/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.organization.OrganizationSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

public record ParticipantResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID organizationId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) boolean active) {

    public static ParticipantResponse from(OrganizationSummary organization) {
        return new ParticipantResponse(
                organization.id().value(), organization.code().value(), organization.name(), organization.active());
    }
}
