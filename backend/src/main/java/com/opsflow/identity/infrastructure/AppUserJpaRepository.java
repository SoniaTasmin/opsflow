/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserJpaRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByOidcIssuerAndOidcSubject(String oidcIssuer, String oidcSubject);

    List<AppUserEntity> findAllByIdIn(Set<UUID> ids);
}
