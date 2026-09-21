/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrgMembershipJpaRepository extends JpaRepository<OrgMembershipEntity, UUID> {

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    Optional<OrgMembershipEntity> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    @Query("select m.organizationId from OrgMembershipEntity m where m.userId = :userId")
    List<UUID> findOrganizationIdsByUserId(UUID userId);

    @Query("select m.userId from OrgMembershipEntity m where m.organizationId = :organizationId order by m.userId")
    List<UUID> findUserIdsByOrganizationId(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    void deleteByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}
