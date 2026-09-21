/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.application;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.domain.OrganizationRepository;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OrganizationAccessService implements OrganizationAccess {

    private final OrganizationRepository organizations;

    OrganizationAccessService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UserId userId, OrganizationId organizationId) {
        return organizations.isMember(userId, organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<OrganizationId> organizationIdsOf(UserId userId) {
        return organizations.organizationIdsOf(userId);
    }
}
