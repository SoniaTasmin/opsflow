/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api.dto;

import com.opsflow.organization.domain.OrganizationView;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = RequiredMode.REQUIRED) String status,
        Instant archivedAt,
        @Schema(requiredMode = RequiredMode.REQUIRED) int memberCount,
        @Schema(requiredMode = RequiredMode.REQUIRED) long version) {

    public static OrganizationResponse from(OrganizationView view) {
        return new OrganizationResponse(
                view.id().value(),
                view.code().value(),
                view.name(),
                view.status().name(),
                view.archivedAt(),
                view.memberCount(),
                view.version());
    }
}
