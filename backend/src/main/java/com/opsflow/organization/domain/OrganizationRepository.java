/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrganizationRepository {

    Optional<Organization> findById(OrganizationId id);

    boolean existsByCode(OrganizationCode code);

    Organization insert(Organization organization, UserId createdBy);

    Organization update(Organization organization, long expectedVersion, UserId updatedBy);

    // Membership checks run on every organization-scoped request, so they are projections
    // rather than aggregate loads: reading a 500-member organization to answer a yes-or-no
    // question would make authorization the most expensive part of a request.
    boolean isMember(UserId userId, OrganizationId organizationId);

    Set<OrganizationId> organizationIdsOf(UserId userId);

    Optional<OrganizationView> viewById(OrganizationId id);

    Optional<OrganizationView> viewByCode(OrganizationCode code);

    List<OrganizationView> viewsById(Set<OrganizationId> ids);

    OrganizationPage search(OrganizationSearchCriteria criteria);

    List<UserId> memberIdsOf(OrganizationId organizationId);
}
