/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

/** A cache of upstream display data so a name renders without calling the provider. No roles. */
public record UserProfile(String username, String displayName, String email) {}
