/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

import com.opsflow.identity.UserSummary;
import com.opsflow.identity.domain.User;
import com.opsflow.identity.domain.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSearchService {

    private final UserRepository users;

    public UserSearchService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Result search(String term, int page, int size) {
        UserRepository.UserPage result = users.search(term, page, size);
        return new Result(
                result.content().stream().map(UserSearchService::toSummary).toList(), result.totalElements());
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.id(), user.username(), user.displayName());
    }

    public record Result(List<UserSummary> content, long totalElements) {}
}
