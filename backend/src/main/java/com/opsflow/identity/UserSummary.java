/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

/**
 * A user as other modules see them: enough to attribute and display work, nothing more.
 *
 * <p>Carries no external identity, no email and no roles — the OIDC subject never leaves the
 * identity module, an address is personal data nobody rendering a name needs, and roles are read
 * per request from the token.
 */
public record UserSummary(UserId userId, String username, String displayName) {}
