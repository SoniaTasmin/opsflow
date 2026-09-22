/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.domain.OptimisticLockConflictException;
import com.opsflow.organization.domain.Organization;
import com.opsflow.organization.domain.OrganizationPage;
import com.opsflow.organization.domain.OrganizationRepository;
import com.opsflow.organization.domain.OrganizationSearchCriteria;
import com.opsflow.organization.domain.OrganizationStatus;
import com.opsflow.organization.domain.OrganizationView;
import com.opsflow.platform.domain.UuidV7;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Commands go through JPA for optimistic locking; read projections use explicit SQL. */
@Repository
class JpaOrganizationRepository implements OrganizationRepository {

    private final OrganizationJpaRepository organizations;
    private final OrgMembershipJpaRepository memberships;
    private final JdbcClient jdbc;
    private final Clock clock;

    JpaOrganizationRepository(
            OrganizationJpaRepository organizations,
            OrgMembershipJpaRepository memberships,
            JdbcClient jdbc,
            Clock clock) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return organizations.findById(id.value()).map(entity -> {
            Set<UserId> members = memberships.findUserIdsByOrganizationId(entity.getId()).stream()
                    .map(UserId::of)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Organization.rehydrate(
                    OrganizationId.of(entity.getId()),
                    new OrganizationCode(entity.getCode()),
                    entity.getName(),
                    OrganizationStatus.valueOf(entity.getStatus()),
                    entity.getArchivedAt(),
                    members,
                    entity.getVersion());
        });
    }

    @Override
    public boolean existsByCode(OrganizationCode code) {
        return organizations.existsByCode(code.value());
    }

    @Override
    public Organization insert(Organization organization, UserId createdBy) {
        OrganizationEntity entity = new OrganizationEntity(
                organization.id().value(),
                organization.code().value(),
                organization.name(),
                organization.status().name(),
                createdBy == null ? null : createdBy.value(),
                Instant.now(clock));
        try {
            organizations.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            // Check-then-insert cannot close the race; the unique index decides.
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "An organization with this code already exists",
                    Map.of("organizationCode", organization.code().value()));
        }
        return findById(organization.id()).orElseThrow();
    }

    @Override
    public Organization update(Organization organization, long expectedVersion, UserId updatedBy) {
        OrganizationEntity entity = organizations
                .findById(organization.id().value())
                .orElseThrow(() -> new IllegalStateException("Organization disappeared while being updated"));

        if (entity.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(organization.id(), expectedVersion);
        }

        entity.apply(
                organization.name(),
                organization.status().name(),
                organization.archivedAt().orElse(null),
                updatedBy == null ? null : updatedBy.value(),
                Instant.now(clock));

        try {
            organizations.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException(organization.id(), expectedVersion);
        }

        syncMembers(organization);
        return findById(organization.id()).orElseThrow();
    }

    private void syncMembers(Organization organization) {
        UUID organizationId = organization.id().value();
        Set<UUID> desired = organization.members().stream().map(UserId::value).collect(Collectors.toSet());
        Set<UUID> current = new LinkedHashSet<>(memberships.findUserIdsByOrganizationId(organizationId));

        current.stream()
                .filter(existing -> !desired.contains(existing))
                .forEach(removed -> memberships.deleteByUserIdAndOrganizationId(removed, organizationId));

        desired.stream()
                .filter(wanted -> !current.contains(wanted))
                .forEach(added ->
                        memberships.save(new OrgMembershipEntity(UuidV7.generate(clock), added, organizationId, null)));

        memberships.flush();
    }

    @Override
    public boolean isMember(UserId userId, OrganizationId organizationId) {
        return memberships.existsByUserIdAndOrganizationId(userId.value(), organizationId.value());
    }

    @Override
    public Set<OrganizationId> organizationIdsOf(UserId userId) {
        return memberships.findOrganizationIdsByUserId(userId.value()).stream()
                .map(OrganizationId::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<UserId> memberIdsOf(OrganizationId organizationId) {
        return memberships.findUserIdsByOrganizationId(organizationId.value()).stream()
                .map(UserId::of)
                .toList();
    }

    @Override
    public Optional<OrganizationView> viewById(OrganizationId id) {
        return jdbc.sql(VIEW_SELECT + " WHERE o.id = :id")
                .param("id", id.value())
                .query(JpaOrganizationRepository::toView)
                .optional();
    }

    @Override
    public Optional<OrganizationView> viewByCode(OrganizationCode code) {
        return jdbc.sql(VIEW_SELECT + " WHERE o.code = :code")
                .param("code", code.value())
                .query(JpaOrganizationRepository::toView)
                .optional();
    }

    @Override
    public List<OrganizationView> viewsById(Set<OrganizationId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(VIEW_SELECT + " WHERE o.id IN (:ids)")
                .param("ids", ids.stream().map(OrganizationId::value).toList())
                .query(JpaOrganizationRepository::toView)
                .list();
    }

    @Override
    public OrganizationPage search(OrganizationSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (criteria.status() != null) {
            where.append(" AND o.status = :status");
        }
        if (criteria.nameOrCodeContains() != null
                && !criteria.nameOrCodeContains().isBlank()) {
            where.append(" AND (lower(o.name) LIKE :term OR lower(o.code) LIKE :term)");
        }
        if (!criteria.isUnrestricted()) {
            // Applied in SQL, not by filtering loaded rows: a filtered page still discloses
            // the total count of what was filtered out.
            if (criteria.visibleTo().isEmpty()) {
                return new OrganizationPage(List.of(), 0);
            }
            where.append(" AND o.id IN (:visibleTo)");
        }

        var contentSpec = jdbc.sql(VIEW_SELECT + where + " ORDER BY o.code LIMIT :limit OFFSET :offset");
        var countSpec = jdbc.sql("SELECT count(*) FROM opsflow.organization o" + where);

        for (var spec : List.of(contentSpec, countSpec)) {
            if (criteria.status() != null) {
                spec.param("status", criteria.status().name());
            }
            if (criteria.nameOrCodeContains() != null
                    && !criteria.nameOrCodeContains().isBlank()) {
                spec.param("term", "%" + criteria.nameOrCodeContains().toLowerCase() + "%");
            }
            if (!criteria.isUnrestricted()) {
                spec.param(
                        "visibleTo",
                        criteria.visibleTo().stream().map(OrganizationId::value).toList());
            }
        }

        List<OrganizationView> content = contentSpec
                .param("limit", criteria.size())
                .param("offset", (long) criteria.page() * criteria.size())
                .query(JpaOrganizationRepository::toView)
                .list();
        long total = countSpec.query(Long.class).single();

        return new OrganizationPage(content, total);
    }

    private static final String VIEW_SELECT =
            """
            SELECT o.id, o.code, o.name, o.status, o.archived_at, o.version,
                   (SELECT count(*) FROM opsflow.org_membership m WHERE m.organization_id = o.id) AS member_count
              FROM opsflow.organization o
            """;

    private static Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OrganizationView toView(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrganizationView(
                OrganizationId.of(rs.getObject("id", UUID.class)),
                new OrganizationCode(rs.getString("code")),
                rs.getString("name"),
                OrganizationStatus.valueOf(rs.getString("status")),
                // The PostgreSQL driver will not convert timestamptz straight to Instant.
                toInstant(rs.getObject("archived_at", java.time.OffsetDateTime.class)),
                rs.getInt("member_count"),
                rs.getLong("version"));
    }
}
