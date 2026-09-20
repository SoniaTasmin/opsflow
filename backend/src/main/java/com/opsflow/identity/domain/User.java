/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

import com.opsflow.identity.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A local projection of a person authenticated by the identity provider.
 *
 * <p>Holds only what the application needs in order to attribute work and render a name: it is
 * never the authority for credentials, and it deliberately carries <strong>no roles</strong>.
 * Roles are read from the token on every request (ADR-0003).
 *
 * <p>The profile fields are upstream display data and may change at any time, so nothing keys on
 * them — identity is {@link #id()}, recognition is {@link #externalIdentity()}.
 */
public final class User {

    private final UserId id;
    private final ExternalIdentity externalIdentity;
    private String username;
    private String displayName;
    private String email;
    private final Instant firstSeenAt;
    private Instant lastSeenAt;

    private User(
            UserId id,
            ExternalIdentity externalIdentity,
            String username,
            String displayName,
            String email,
            Instant firstSeenAt,
            Instant lastSeenAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.externalIdentity = Objects.requireNonNull(externalIdentity, "externalIdentity is required");
        this.username = requireText(username, "username");
        this.displayName = requireText(displayName, "displayName");
        this.email = email;
        this.firstSeenAt = Objects.requireNonNull(firstSeenAt, "firstSeenAt is required");
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt is required");
    }

    /** Creates the local record the first time a person authenticates. */
    public static User provision(
            UserId id,
            ExternalIdentity externalIdentity,
            String username,
            String displayName,
            String email,
            Instant now) {
        return new User(id, externalIdentity, username, displayName, email, now, now);
    }

    /** Rehydrates an existing user from storage. */
    public static User rehydrate(
            UserId id,
            ExternalIdentity externalIdentity,
            String username,
            String displayName,
            String email,
            Instant firstSeenAt,
            Instant lastSeenAt) {
        return new User(id, externalIdentity, username, displayName, email, firstSeenAt, lastSeenAt);
    }

    /**
     * Refreshes the profile from the current token and records the visit.
     *
     * @return whether anything actually changed, so an unchanged user costs no write
     */
    public boolean recordSeen(String username, String displayName, String email, Instant now, Duration staleAfter) {
        boolean profileChanged = !Objects.equals(this.username, username)
                || !Objects.equals(this.displayName, displayName)
                || !Objects.equals(this.email, email);
        boolean lastSeenStale = lastSeenAt.plus(staleAfter).isBefore(now);

        if (profileChanged) {
            this.username = requireText(username, "username");
            this.displayName = requireText(displayName, "displayName");
            this.email = email;
        }
        if (profileChanged || lastSeenStale) {
            this.lastSeenAt = now;
            return true;
        }
        return false;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public UserId id() {
        return id;
    }

    public ExternalIdentity externalIdentity() {
        return externalIdentity;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public Optional<String> email() {
        return Optional.ofNullable(email);
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }
}
