/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.identity.domain.ExternalIdentity;
import com.opsflow.identity.domain.User;

/**
 * Hand-written mapping between the domain type and its persistence shape.
 *
 * <p>No mapping framework: a generated mapper hides a dropped field behind an annotation
 * processor, and this is short enough that the cost of writing it is lower than the cost of not
 * being able to read it.
 */
final class UserMapper {

    private UserMapper() {}

    static User toDomain(AppUserEntity entity) {
        return User.rehydrate(
                UserId.of(entity.getId()),
                new ExternalIdentity(entity.getOidcIssuer(), entity.getOidcSubject()),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt());
    }

    static AppUserEntity toEntity(User user) {
        return new AppUserEntity(
                user.id().value(),
                user.externalIdentity().issuer(),
                user.externalIdentity().subject(),
                user.username(),
                user.displayName(),
                user.email().orElse(null),
                user.firstSeenAt(),
                user.lastSeenAt());
    }
}
