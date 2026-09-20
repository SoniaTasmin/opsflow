/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.identity.domain.DuplicateExternalIdentityException;
import com.opsflow.identity.domain.ExternalIdentity;
import com.opsflow.identity.domain.User;
import com.opsflow.identity.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Adapts the domain's {@link UserRepository} to Spring Data JPA. */
@Repository
class JpaUserRepository implements UserRepository {

    private final AppUserJpaRepository jpa;

    JpaUserRepository(AppUserJpaRepository jpa) {
        this.jpa = jpa;
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
    public void update(User user) {
        AppUserEntity entity = jpa.findById(user.id().value())
                .orElseThrow(() -> new IllegalStateException("User disappeared while being updated"));
        entity.applyProfile(user.username(), user.displayName(), user.email().orElse(null), user.lastSeenAt());
        jpa.save(entity);
    }
}
