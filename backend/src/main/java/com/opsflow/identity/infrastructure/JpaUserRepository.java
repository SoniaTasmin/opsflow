/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.identity.domain.DuplicateExternalIdentityException;
import com.opsflow.identity.domain.ExternalIdentity;
import com.opsflow.identity.domain.User;
import com.opsflow.identity.domain.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRepository implements UserRepository {

    private final AppUserJpaRepository jpa;
    private final JdbcClient jdbc;

    JpaUserRepository(AppUserJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByExternalIdentity(ExternalIdentity externalIdentity) {
        return jpa.findByOidcIssuerAndOidcSubject(externalIdentity.issuer(), externalIdentity.subject())
                .map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserMapper::toDomain);
    }

    @Override
    public List<User> findAllById(Set<UserId> ids) {
        // The one place a typed id becomes a raw UUID: the persistence boundary.
        Set<UUID> rawIds = ids.stream().map(UserId::value).collect(java.util.stream.Collectors.toSet());
        return jpa.findAllByIdIn(rawIds).stream().map(UserMapper::toDomain).toList();
    }

    @Override
    public User insert(User user) {
        try {
            // saveAndFlush, not save: the unique-constraint violation must surface here,
            // where it can be translated, rather than at an arbitrary later flush.
            return UserMapper.toDomain(jpa.saveAndFlush(UserMapper.toEntity(user)));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateExternalIdentityException(user.externalIdentity(), ex);
        }
    }

    @Override
    public UserPage search(String term, int page, int size) {
        boolean hasTerm = term != null && !term.isBlank();
        String where = hasTerm ? " WHERE lower(u.username) LIKE :term OR lower(u.display_name) LIKE :term" : "";

        var content = jdbc.sql(
                """
                SELECT u.id, u.oidc_issuer, u.oidc_subject, u.username, u.display_name, u.email,
                       u.first_seen_at, u.last_seen_at
                  FROM opsflow.app_user u
                """
                        + where
                        // Username is not unique, so it alone is not a stable sort key.
                        + " ORDER BY lower(u.username), u.id LIMIT :limit OFFSET :offset");
        var count = jdbc.sql("SELECT count(*) FROM opsflow.app_user u" + where);

        if (hasTerm) {
            String pattern = "%" + term.toLowerCase() + "%";
            content.param("term", pattern);
            count.param("term", pattern);
        }

        List<User> rows = content.param("limit", size)
                .param("offset", (long) page * size)
                .query(JpaUserRepository::toDomain)
                .list();
        return new UserPage(rows, count.query(Long.class).single());
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static User toDomain(ResultSet rs, int rowNum) throws SQLException {
        return User.rehydrate(
                UserId.of(rs.getObject("id", UUID.class)),
                new com.opsflow.identity.domain.ExternalIdentity(
                        rs.getString("oidc_issuer"), rs.getString("oidc_subject")),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                toInstant(rs.getObject("first_seen_at", OffsetDateTime.class)),
                toInstant(rs.getObject("last_seen_at", OffsetDateTime.class)));
    }

    @Override
    public void update(User user) {
        AppUserEntity entity = jpa.findById(user.id().value())
                .orElseThrow(() -> new IllegalStateException("User disappeared while being updated"));
        entity.applyProfile(user.username(), user.displayName(), user.email().orElse(null), user.lastSeenAt());
        jpa.save(entity);
    }
}
