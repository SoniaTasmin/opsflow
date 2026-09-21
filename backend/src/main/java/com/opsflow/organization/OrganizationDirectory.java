/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrganizationDirectory {

    Optional<OrganizationSummary> findById(OrganizationId id);

    Optional<OrganizationSummary> findByCode(OrganizationCode code);

    List<OrganizationSummary> findAllById(Set<OrganizationId> ids);
}
