/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import java.util.Optional;

/**
 * Authorship, available anywhere including threads with no security context. Separate from
 * {@link CurrentActorProvider} so code cannot ask for roles where they cannot exist and receive
 * an empty set that quietly passes a check.
 */
public interface CurrentUserProvider {

    Optional<UserId> currentUserId();
}
