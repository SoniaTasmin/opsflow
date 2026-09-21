/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import com.opsflow.organization.OrganizationSummary;
import java.util.UUID;

public record ParticipantResponse(UUID organizationId, String code, String name, boolean active) {

    public static ParticipantResponse from(OrganizationSummary organization) {
        return new ParticipantResponse(
                organization.id().value(), organization.code().value(), organization.name(), organization.active());
    }
}
