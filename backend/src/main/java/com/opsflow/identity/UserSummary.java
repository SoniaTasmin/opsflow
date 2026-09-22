/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

/**
 * No external identity, email or roles: the subject stays inside this module, an address is
 * personal data nobody rendering a name needs, and roles come from the token.
 */
public record UserSummary(UserId userId, String username, String displayName) {}
