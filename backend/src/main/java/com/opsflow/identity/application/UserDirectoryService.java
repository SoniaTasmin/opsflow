/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

import com.opsflow.identity.UserDirectory;
import com.opsflow.identity.UserId;
import com.opsflow.identity.UserSummary;
import com.opsflow.identity.domain.User;
import com.opsflow.identity.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(UserId userId) {
        return users.findById(userId).map(UserDirectoryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummary> findAllById(Set<UserId> userIds) {
        return users.findAllById(userIds).stream()
                .map(UserDirectoryService::toSummary)
                .toList();
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.id(), user.username(), user.displayName());
    }
}
