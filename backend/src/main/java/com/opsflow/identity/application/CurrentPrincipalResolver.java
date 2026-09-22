/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.identity.CurrentUserProfile;
import com.opsflow.identity.CurrentUserProvider;
import com.opsflow.identity.UserId;
import com.opsflow.identity.domain.ExternalIdentity;
import com.opsflow.identity.domain.User;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.security.JwtRoleConverter;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Memoised per request so a request touching several components does not repeat the identity
 * lookup. Not a cross-request cache: the (issuer, subject) mapping is immutable and caching it
 * between requests is an optimisation left until measurement asks for it.
 */
@Component
class CurrentPrincipalResolver implements CurrentActorProvider, CurrentUserProvider, CurrentUserProfile {

    private static final String REQUEST_ATTRIBUTE = CurrentPrincipalResolver.class.getName() + ".actor";

    private final UserProvisioningService provisioning;

    CurrentPrincipalResolver(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public Actor requireActor() {
        return currentActor()
                .orElseThrow(() -> new IllegalStateException(
                        "No authenticated request in scope. Asynchronous work must carry its own durable "
                                + "authority rather than asking for an Actor, which has no meaning off-request."));
    }

    @Override
    public Optional<UserId> currentUserId() {
        return currentActor().map(Actor::userId);
    }

    @Override
    public Optional<SelfProfile> current() {
        return currentJwt().map(jwt -> {
            User user = provisioning.resolve(externalIdentityOf(jwt), profileOf(jwt));
            return new SelfProfile(
                    user.id(), user.username(), user.displayName(), user.email().orElse(null));
        });
    }

    private Optional<Actor> currentActor() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof Actor actor) {
                return Optional.of(actor);
            }
        }

        Optional<Actor> resolved = resolveFromSecurityContext();
        resolved.ifPresent(actor -> {
            if (attributes != null) {
                attributes.setAttribute(REQUEST_ATTRIBUTE, actor, RequestAttributes.SCOPE_REQUEST);
            }
        });
        return resolved;
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(jwtAuthentication.getToken());
    }

    private static ExternalIdentity externalIdentityOf(Jwt jwt) {
        return new ExternalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
    }

    private Optional<Actor> resolveFromSecurityContext() {
        Optional<Jwt> maybeJwt = currentJwt();
        if (maybeJwt.isEmpty()) {
            return Optional.empty();
        }

        Jwt jwt = maybeJwt.get();
        User user = provisioning.resolve(externalIdentityOf(jwt), profileOf(jwt));

        // Roles come from this request's token every time, never from the stored user.
        Set<Role> roles = JwtRoleConverter.rolesOf(jwt);
        return Optional.of(new Actor(user.id(), roles));
    }

    private static UserProfile profileOf(Jwt jwt) {
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
        String displayName = firstNonBlank(jwt.getClaimAsString("name"), username);
        return new UserProfile(username, displayName, jwt.getClaimAsString("email"));
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
