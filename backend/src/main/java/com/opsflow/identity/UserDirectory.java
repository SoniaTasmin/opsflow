/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** {@link #findAllById(Set)} exists so a caller rendering a list resolves names in one query. */
public interface UserDirectory {

    Optional<UserSummary> findById(UserId userId);

    List<UserSummary> findAllById(Set<UserId> userIds);
}
