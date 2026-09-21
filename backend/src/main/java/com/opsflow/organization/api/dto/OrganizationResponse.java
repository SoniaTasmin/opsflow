/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api.dto;

import com.opsflow.organization.domain.OrganizationView;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id, String code, String name, String status, Instant archivedAt, int memberCount, long version) {

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
