/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.identity.UserDirectory;
import com.opsflow.identity.UserSummary;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the caller is, according to their token and this application's records.
 *
 * <p>Also the endpoint that provisions a first-time user, as a side effect of resolving them,
 * which is why a brand-new account works without any administrative step.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Identity", description = "The authenticated user")
public class MeController {

    private final CurrentActorProvider currentActor;
    private final UserDirectory userDirectory;

    public MeController(CurrentActorProvider currentActor, UserDirectory userDirectory) {
        this.currentActor = currentActor;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    @Operation(
            summary = "Describe the authenticated user",
            description = "Returns the caller's application identity and the global roles carried by their token. "
                    + "Roles are read from the token on every request and are never stored.")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Actor actor = currentActor.requireActor();
        UserSummary user =
                userDirectory.findById(actor.userId()).orElseThrow(() -> NotFoundException.of("User", actor.userId()));

        List<String> roles = actor.roles().stream()
                .map(Role::name)
                .sorted(Comparator.naturalOrder())
                .toList();

        return ResponseEntity.ok(new CurrentUserResponse(
                // Typed ids are unwrapped here, at the serialization boundary, and nowhere else.
                user.userId().value(), user.username(), user.displayName(), jwt.getClaimAsString("email"), roles));
    }
}
