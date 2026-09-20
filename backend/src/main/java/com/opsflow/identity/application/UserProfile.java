/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

/**
 * Display data carried by the current token.
 *
 * <p>Held locally so the UI can render a name without calling the identity provider per row. It
 * is a cache of upstream data, never the authority for it — and it carries no roles.
 */
public record UserProfile(String username, String displayName, String email) {}
