/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.domain;

import java.util.Optional;

/**
 * Read from the token on every request and never persisted: a stored copy is a
 * privilege-escalation bug with a delay fuse. Which organizations they may act for is membership
 * (ADR-0003).
 */
public enum Role {
    USER,

    /** Central: review authority across all organizations. */
    REVIEWER,

    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Ignores anything unrecognised: Keycloak and other applications put their own roles in the realm. */
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
