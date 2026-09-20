/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import java.util.Optional;

/**
 * The application user behind the current work, carrying <strong>no roles</strong>.
 *
 * <p>Available wherever work happens, including background threads that have no security context
 * and therefore no roles to speak of. This is what populates authorship fields.
 *
 * <p>Kept deliberately separate from {@link CurrentActorProvider}: authorship is durable and
 * knowable anywhere, while authorization context is ephemeral and exists only during an
 * authenticated request. Conflating them is what leads to code asking for roles somewhere they
 * cannot exist and receiving an empty set that silently passes a check.
 */
public interface CurrentUserProvider {

    Optional<UserId> currentUserId();
}
