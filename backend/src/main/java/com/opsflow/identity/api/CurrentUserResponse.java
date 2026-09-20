/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * What the signed-in user is told about themselves.
 *
 * <p>Deliberately excludes the OIDC subject and issuer: how this person is recognised upstream is
 * an internal detail, and nothing client-side has a use for it.
 *
 * <p>{@code email} comes from the caller's own token rather than from the user directory, which
 * intentionally carries no email — other modules use the directory to render names, and an
 * address is personal data none of them need.
 *
 * <p>Organization membership joins this response in M3, when it exists.
 */
public record CurrentUserResponse(UUID id, String username, String displayName, String email, List<String> roles) {}
