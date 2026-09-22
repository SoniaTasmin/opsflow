/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Shaped by the table; {@code User} is shaped by behaviour. Never leaves infrastructure. */
@Entity
@Table(name = "app_user", schema = "opsflow")
public class AppUserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "oidc_issuer", nullable = false, updatable = false, length = 255)
    private String oidcIssuer;

    @Column(name = "oidc_subject", nullable = false, updatable = false, length = 255)
    private String oidcSubject;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppUserEntity() {
        // Required by JPA.
    }

    AppUserEntity(
            UUID id,
            String oidcIssuer,
            String oidcSubject,
            String username,
            String displayName,
            String email,
            Instant firstSeenAt,
            Instant lastSeenAt) {
        this.id = id;
        this.oidcIssuer = oidcIssuer;
        this.oidcSubject = oidcSubject;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    void applyProfile(String username, String displayName, String email, Instant lastSeenAt) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.lastSeenAt = lastSeenAt;
    }

    UUID getId() {
        return id;
    }

    String getOidcIssuer() {
        return oidcIssuer;
    }

    String getOidcSubject() {
        return oidcSubject;
    }

    String getUsername() {
        return username;
    }

    String getDisplayName() {
        return displayName;
    }

    String getEmail() {
        return email;
    }

    Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
