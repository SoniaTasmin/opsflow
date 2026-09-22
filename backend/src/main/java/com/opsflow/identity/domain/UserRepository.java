/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

import com.opsflow.identity.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository {

    Optional<User> findByExternalIdentity(ExternalIdentity externalIdentity);

    Optional<User> findById(UserId id);

    List<User> findAllById(Set<UserId> ids);

    /** @throws DuplicateExternalIdentityException when another request inserted it concurrently */
    User insert(User user);

    void update(User user);

    UserPage search(String term, int page, int size);

    record UserPage(List<User> content, long totalElements) {}
}
