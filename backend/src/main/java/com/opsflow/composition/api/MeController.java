/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.api;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.identity.CurrentUserProfile;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spans identity and organization, so it lives in the composition layer: the module order
 * forbids identity from asking organization anything.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Identity", description = "The authenticated user")
public class MeController {

    private final CurrentActorProvider currentActor;
    private final CurrentUserProfile profiles;
    private final OrganizationAccess organizationAccess;
    private final OrganizationDirectory organizations;

    public MeController(
            CurrentActorProvider currentActor,
            CurrentUserProfile profiles,
            OrganizationAccess organizationAccess,
            OrganizationDirectory organizations) {
        this.currentActor = currentActor;
        this.profiles = profiles;
        this.organizationAccess = organizationAccess;
        this.organizations = organizations;
    }

    @GetMapping
    @Operation(
            summary = "Describe the authenticated user",
            description = "Roles come from the token on every request and are never stored; "
                    + "membership comes from the database.")
    public ResponseEntity<CurrentUserResponse> me() {
        Actor actor = currentActor.requireActor();
        CurrentUserProfile.SelfProfile profile =
                profiles.current().orElseThrow(() -> NotFoundException.of("User", actor.userId()));

        List<String> roles = actor.roles().stream()
                .map(Role::name)
                .sorted(Comparator.naturalOrder())
                .toList();

        List<CurrentUserResponse.MembershipResponse> memberships =
                organizations.findAllById(organizationAccess.organizationIdsOf(actor.userId())).stream()
                        .sorted(Comparator.comparing(summary -> summary.code().value()))
                        .map(MeController::toMembership)
                        .toList();

        return ResponseEntity.ok(new CurrentUserResponse(
                profile.userId().value(),
                profile.username(),
                profile.displayName(),
                profile.email(),
                roles,
                memberships));
    }

    private static CurrentUserResponse.MembershipResponse toMembership(OrganizationSummary organization) {
        return new CurrentUserResponse.MembershipResponse(
                organization.id().value(), organization.code().value(), organization.name(), organization.active());
    }
}
