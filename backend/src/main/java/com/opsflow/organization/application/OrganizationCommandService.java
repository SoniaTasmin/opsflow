/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.application;

import com.opsflow.identity.CurrentUserProvider;
import com.opsflow.identity.UserDirectory;
import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.domain.Organization;
import com.opsflow.organization.domain.OrganizationRepository;
import com.opsflow.organization.domain.OrganizationView;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.platform.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationCommandService {

    private final OrganizationRepository organizations;
    private final UserDirectory users;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public OrganizationCommandService(
            OrganizationRepository organizations, UserDirectory users, CurrentUserProvider currentUser, Clock clock) {
        this.organizations = organizations;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public OrganizationView create(OrganizationCode code, String name) {
        if (organizations.existsByCode(code)) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "An organization with this code already exists",
                    Map.of("organizationCode", code.value()));
        }
        Organization organization = Organization.create(OrganizationId.generate(clock), code, name);
        organizations.insert(organization, actorId());
        return organizations.viewById(organization.id()).orElseThrow();
    }

    @Transactional
    public OrganizationView rename(OrganizationId id, String newName, long expectedVersion) {
        Organization organization = load(id);
        organization.rename(newName);
        organizations.update(organization, expectedVersion, actorId());
        return organizations.viewById(id).orElseThrow();
    }

    @Transactional
    public OrganizationView archive(OrganizationId id, long expectedVersion) {
        Organization organization = load(id);
        organization.archive(Instant.now(clock));
        organizations.update(organization, expectedVersion, actorId());
        return organizations.viewById(id).orElseThrow();
    }

    @Transactional
    public boolean addMember(OrganizationId id, UserId userId) {
        Organization organization = load(id);
        requireKnownUser(userId);

        boolean added = organization.addMember(userId);
        if (added) {
            organizations.update(organization, organization.version(), actorId());
        }
        return added;
    }

    @Transactional
    public boolean removeMember(OrganizationId id, UserId userId) {
        Organization organization = load(id);

        boolean removed = organization.removeMember(userId);
        if (removed) {
            organizations.update(organization, organization.version(), actorId());
        }
        return removed;
    }

    private Organization load(OrganizationId id) {
        return organizations.findById(id).orElseThrow(() -> NotFoundException.of("Organization", id));
    }

    private void requireKnownUser(UserId userId) {
        // Users are provisioned when they first authenticate, so an unknown id is a mistake
        // rather than a user to create here.
        if (users.findById(userId).isEmpty()) {
            throw NotFoundException.of("User", userId);
        }
    }

    /** Authorship, not authorization: this works off-request, where roles have no meaning. */
    private UserId actorId() {
        return currentUser.currentUserId().orElse(null);
    }
}
