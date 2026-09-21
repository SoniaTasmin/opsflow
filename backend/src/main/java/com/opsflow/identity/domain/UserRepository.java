/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

import com.opsflow.identity.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence for users, declared by the domain in the terms the domain uses.
 *
 * <p>The implementation lives in {@code infrastructure} and is the only thing that knows about
 * JPA or SQL.
 */
public interface UserRepository {

    Optional<User> findByExternalIdentity(ExternalIdentity externalIdentity);

    Optional<User> findById(UserId id);

    List<User> findAllById(Set<UserId> ids);

    /**
     * Inserts a newly provisioned user.
     *
     * @throws DuplicateExternalIdentityException if the external identity was inserted
     *     concurrently by another request
     */
    User insert(User user);

    void update(User user);

    UserPage search(String term, int page, int size);

    record UserPage(List<User> content, long totalElements) {}
}
