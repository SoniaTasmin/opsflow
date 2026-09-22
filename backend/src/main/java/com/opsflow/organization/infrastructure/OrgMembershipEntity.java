/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Persistence shape of {@code opsflow.org_membership}. No role column: membership carries none. */
@Entity
@Table(name = "org_membership", schema = "opsflow")
public class OrgMembershipEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected OrgMembershipEntity() {}

    OrgMembershipEntity(UUID id, UUID userId, UUID organizationId, UUID createdBy) {
        this.id = id;
        this.userId = userId;
        this.organizationId = organizationId;
        this.createdBy = createdBy;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getOrganizationId() {
        return organizationId;
    }
}
