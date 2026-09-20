/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.domain;

import java.util.Optional;

/**
 * A global role, and the only source of "what kind of user this is".
 *
 * <p>Roles come from the Keycloak realm on every request and are <strong>never persisted</strong>:
 * a stored copy is a privilege-escalation bug with a delay fuse. Which organizations a user may
 * act for is a separate question, answered by membership in the database (ADR-0003).
 */
public enum Role {

    /** May work with the organizations they belong to. */
    USER,

    /** Central reviewer: review authority across all organizations. */
    REVIEWER,

    /** Platform administration. */
    ADMIN;

    /** Spring Security's convention, applied when mapping token claims to authorities. */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * Resolves a realm role name, ignoring anything unrecognised.
     *
     * <p>Keycloak issues roles of its own ({@code offline_access}, {@code uma_authorization}),
     * and a realm may carry roles for other applications. Only the three roles this application
     * defines ever become authorities.
     */
    public static Optional<Role> fromRealmRole(String realmRole) {
        if (realmRole == null) {
            return Optional.empty();
        }
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(realmRole)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
