/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

import com.opsflow.identity.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Enough to attribute work and render a name, and no roles: those come from the token (ADR-0003).
 * Profile fields are upstream data that can change, so nothing keys on them.
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

    public static User provision(
            UserId id,
            ExternalIdentity externalIdentity,
            String username,
            String displayName,
            String email,
            Instant now) {
        return new User(id, externalIdentity, username, displayName, email, now, now);
    }

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

    /** @return whether anything changed, so an unchanged user costs no write */
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
