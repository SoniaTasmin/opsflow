/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence shape of {@code opsflow.organization}. Never leaves this package. */
@Entity
@Table(name = "organization", schema = "opsflow")
public class OrganizationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrganizationEntity() {}

    OrganizationEntity(UUID id, String code, String name, String status, UUID createdBy, Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.updatedAt = now;
    }

    void apply(String name, String status, Instant archivedAt, UUID updatedBy, Instant now) {
        this.name = name;
        this.status = status;
        this.archivedAt = archivedAt;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getStatus() {
        return status;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }

    long getVersion() {
        return version;
    }
}
