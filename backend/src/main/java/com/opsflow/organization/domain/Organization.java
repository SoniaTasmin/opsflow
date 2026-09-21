/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Membership is a set of users and carries no role: what a user may do comes from their token,
 * what they may do it to comes from this set (ADR-0003).
 */
public final class Organization {

    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 120;

    private final OrganizationId id;
    private final OrganizationCode code;
    private String name;
    private OrganizationStatus status;
    private Instant archivedAt;
    private final Set<UserId> members;
    private final long version;

    private Organization(
            OrganizationId id,
            OrganizationCode code,
            String name,
            OrganizationStatus status,
            Instant archivedAt,
            Set<UserId> members,
            long version) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = requireValidName(name);
        this.status = Objects.requireNonNull(status, "status is required");
        this.archivedAt = archivedAt;
        this.members = new LinkedHashSet<>(members == null ? Set.of() : members);
        this.version = version;
    }

    public static Organization create(OrganizationId id, OrganizationCode code, String name) {
        return new Organization(id, code, name, OrganizationStatus.ACTIVE, null, Set.of(), 0L);
    }

    public static Organization rehydrate(
            OrganizationId id,
            OrganizationCode code,
            String name,
            OrganizationStatus status,
            Instant archivedAt,
            Set<UserId> members,
            long version) {
        return new Organization(id, code, name, status, archivedAt, members, version);
    }

    public void rename(String newName) {
        assertActive("rename");
        this.name = requireValidName(newName);
    }

    public void archive(Instant now) {
        if (status == OrganizationStatus.ARCHIVED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Organization is already archived",
                    Map.of("organizationId", id.toString()));
        }
        this.status = OrganizationStatus.ARCHIVED;
        this.archivedAt = Objects.requireNonNull(now, "now is required");
    }

    /** @return whether a membership was created, so a caller can answer 201 versus 204 */
    public boolean addMember(UserId userId) {
        Objects.requireNonNull(userId, "userId is required");
        assertActive("add a member to");
        return members.add(userId);
    }

    /**
     * Permitted on an archived organization: revoking access must never be blocked by the state
     * of what access is being revoked from.
     */
    public boolean removeMember(UserId userId) {
        Objects.requireNonNull(userId, "userId is required");
        return members.remove(userId);
    }

    public boolean hasMember(UserId userId) {
        return members.contains(userId);
    }

    private void assertActive(String action) {
        if (status != OrganizationStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "ORGANIZATION_ARCHIVED",
                    "Cannot " + action + " an archived organization",
                    Map.of("organizationId", id.toString(), "status", status.name()));
        }
    }

    private static String requireValidName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < NAME_MIN || trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException(
                    "Organization name must be " + NAME_MIN + "-" + NAME_MAX + " characters");
        }
        return trimmed;
    }

    public OrganizationId id() {
        return id;
    }

    public OrganizationCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public OrganizationStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public Optional<Instant> archivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public Set<UserId> members() {
        return Collections.unmodifiableSet(members);
    }

    public long version() {
        return version;
    }
}
