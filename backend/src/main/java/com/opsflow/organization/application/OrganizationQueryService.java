/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.UserDirectory;
import com.opsflow.identity.UserId;
import com.opsflow.identity.UserSummary;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.domain.OrganizationPage;
import com.opsflow.organization.domain.OrganizationRepository;
import com.opsflow.organization.domain.OrganizationSearchCriteria;
import com.opsflow.organization.domain.OrganizationStatus;
import com.opsflow.organization.domain.OrganizationView;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.error.NotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationQueryService {

    private final OrganizationRepository organizations;
    private final OrganizationAccess access;
    private final UserDirectory users;

    public OrganizationQueryService(
            OrganizationRepository organizations, OrganizationAccess access, UserDirectory users) {
        this.organizations = organizations;
        this.access = access;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public OrganizationPage search(Actor actor, String term, OrganizationStatus status, int page, int size) {
        Set<OrganizationId> visibleTo = seesEveryOrganization(actor) ? null : access.organizationIdsOf(actor.userId());
        return organizations.search(new OrganizationSearchCriteria(term, status, visibleTo, page, size));
    }

    /**
     * Not found rather than forbidden when the caller cannot see it: a 403 would confirm that an
     * organization with that id exists.
     */
    @Transactional(readOnly = true)
    public OrganizationView requireVisible(Actor actor, OrganizationId id) {
        OrganizationView view = organizations.viewById(id).orElseThrow(() -> NotFoundException.of("Organization", id));

        if (!seesEveryOrganization(actor) && !access.isMember(actor.userId(), id)) {
            throw NotFoundException.of("Organization", id);
        }
        return view;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> membersOf(Actor actor, OrganizationId id) {
        requireVisible(actor, id);
        List<UserId> memberIds = organizations.memberIdsOf(id);
        return users.findAllById(Set.copyOf(memberIds));
    }

    @Transactional(readOnly = true)
    public List<OrganizationView> organizationsOf(UserId userId) {
        return organizations.viewsById(access.organizationIdsOf(userId));
    }

    private boolean seesEveryOrganization(Actor actor) {
        return actor.hasRole(Role.ADMIN) || actor.hasRole(Role.REVIEWER);
    }
}
