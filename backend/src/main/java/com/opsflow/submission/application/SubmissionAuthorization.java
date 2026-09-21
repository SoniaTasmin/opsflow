/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.error.AccessDeniedDomainException;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.submission.SubmissionId;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Organization-scoped authorization, which can only be decided once the submission has been
 * loaded and its organization is known. The coarse role gate is declarative on the controller.
 */
@Component
class SubmissionAuthorization {

    private static final Logger log = LoggerFactory.getLogger(SubmissionAuthorization.class);

    private final CurrentActorProvider currentActor;
    private final OrganizationAccess organizations;

    SubmissionAuthorization(CurrentActorProvider currentActor, OrganizationAccess organizations) {
        this.currentActor = currentActor;
        this.organizations = organizations;
    }

    Actor actor() {
        return currentActor.requireActor();
    }

    /**
     * A caller who cannot see the owning organization gets the same 404 as one asking about a
     * submission that does not exist. A 403 would confirm the identifier is real, which is
     * exactly what enumerating it is meant to discover. The real reason is logged.
     */
    void requireVisibility(Actor actor, SubmissionId submissionId, OrganizationId organizationId) {
        if (canSeeEverything(actor) || organizations.isMember(actor.userId(), organizationId)) {
            return;
        }
        log.info(
                "Hiding submission from a caller outside its organization submissionId={} organizationId={} userId={}",
                submissionId,
                organizationId,
                actor.userId());
        throw NotFoundException.of("Submission", submissionId);
    }

    /** Authoring is membership work: an administrator may act centrally, a reviewer may not. */
    void requireAuthoring(Actor actor, OrganizationId organizationId) {
        if (actor.isAdmin()) {
            return;
        }
        if (actor.hasRole(Role.USER) && organizations.isMember(actor.userId(), organizationId)) {
            return;
        }
        throw new AccessDeniedDomainException("Only a member of this organization can author its submissions");
    }

    /** Null means "every organization"; an empty set would mean "none", which is not the same. */
    Set<OrganizationId> visibleOrganizations(Actor actor) {
        return canSeeEverything(actor) ? null : organizations.organizationIdsOf(actor.userId());
    }

    private static boolean canSeeEverything(Actor actor) {
        return actor.isAdmin() || actor.hasRole(Role.REVIEWER);
    }
}
