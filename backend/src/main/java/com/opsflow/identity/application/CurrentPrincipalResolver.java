/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
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
 * Resolves the current request's token into an application {@link Actor}, provisioning the local
 * user on first sight.
 *
 * <p>The resolved actor is memoised in request scope. Without that, a request touching several
 * components would repeat the same identity lookup for each one — the classic N+1, just on the
 * authentication path rather than a query.
 *
 * <p>This is not a cross-request cache. Caching the immutable (issuer, subject) to user-id
 * mapping between requests is an obvious further optimisation, deliberately left unbuilt until
 * measurement shows the lookup matters.
 */
@Component
class CurrentPrincipalResolver implements CurrentActorProvider, CurrentUserProvider {

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

    private Optional<Actor> resolveFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Jwt jwt = jwtAuthentication.getToken();
        ExternalIdentity externalIdentity = new ExternalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
        User user = provisioning.resolve(externalIdentity, profileOf(jwt));

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
