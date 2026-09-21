/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.application;

import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.organization.domain.OrganizationRepository;
import com.opsflow.organization.domain.OrganizationView;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OrganizationDirectoryService implements OrganizationDirectory {

    private final OrganizationRepository organizations;

    OrganizationDirectoryService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationSummary> findById(OrganizationId id) {
        return organizations.viewById(id).map(OrganizationDirectoryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationSummary> findByCode(OrganizationCode code) {
        return organizations.viewByCode(code).map(OrganizationDirectoryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationSummary> findAllById(Set<OrganizationId> ids) {
        return organizations.viewsById(ids).stream()
                .map(OrganizationDirectoryService::toSummary)
                .toList();
    }

    static OrganizationSummary toSummary(OrganizationView view) {
        return new OrganizationSummary(view.id(), view.code(), view.name(), view.isActive());
    }
}
