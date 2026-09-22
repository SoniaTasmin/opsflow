/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import com.opsflow.platform.domain.Role;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Exists only where a real authenticated request does; asynchronous work carries durable
 * authority instead, which has no roles.
 *
 * @param roles from this request's token, and legitimately empty for a user granted nothing
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

    /** False for an empty role set: no path here treats "no roles" as unrestricted. */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
