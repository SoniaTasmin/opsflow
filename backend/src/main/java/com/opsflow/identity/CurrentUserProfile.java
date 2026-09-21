/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import java.util.Optional;

/**
 * Self-scoped, so unlike {@link UserDirectory} it may carry an email address. Also keeps token
 * parsing inside identity.
 */
public interface CurrentUserProfile {

    Optional<SelfProfile> current();

    record SelfProfile(UserId userId, String username, String displayName, String email) {}
}
