/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import java.time.Instant;

public record OrganizationView(
        OrganizationId id,
        OrganizationCode code,
        String name,
        OrganizationStatus status,
        Instant archivedAt,
        int memberCount,
        long version) {

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }
}
