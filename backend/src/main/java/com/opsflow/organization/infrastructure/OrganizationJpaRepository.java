/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findByCode(String code);

    boolean existsByCode(String code);
}
