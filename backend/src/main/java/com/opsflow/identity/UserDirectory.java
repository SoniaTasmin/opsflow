/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Published lookup of users by identifier, for modules that need to render who did something.
 *
 * <p>{@link #findAllById(Set)} exists so a caller rendering a list resolves every name in one
 * query rather than one per row.
 */
public interface UserDirectory {

    Optional<UserSummary> findById(UserId userId);

    List<UserSummary> findAllById(Set<UserId> userIds);
}
