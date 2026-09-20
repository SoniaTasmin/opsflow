/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import com.opsflow.platform.domain.Role;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who is making the current request, and what kind of user they are.
 *
 * <p>Carries a typed {@link UserId} — the application's own identifier, never the OIDC
 * {@code sub}, which stays inside the identity module and appears in no foreign key (ADR-0003).
 * The type matters as much as the value: this domain is full of interchangeable UUIDs, and a
 * raw one crossing a module boundary is a bug the compiler cannot see.
 *
 * <p>An {@code Actor} exists only where a real authenticated request does. Asynchronous work
 * carries durable authority instead, which is a different concept: it has no roles, because
 * roles live in a token that a background thread does not have.
 *
 * @param userId the application-owned user identifier
 * @param roles global roles from this request's token; may legitimately be empty
 */
public record Actor(UserId userId, Set<Role> roles) {

    public Actor {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        roles = roles == null || roles.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(Role.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    /**
     * Whether this actor holds the role.
     *
     * <p>Returns {@code false} for an actor with no roles. There is deliberately no path by which
     * an empty role set means "unrestricted": fail-open authorization is how an empty or
     * partially-built principal turns into an accidental administrator.
     */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
