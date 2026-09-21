/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import com.opsflow.identity.UserSummary;
import java.util.UUID;

public record UserResponse(UUID id, String username, String displayName) {

    public static UserResponse from(UserSummary user) {
        return new UserResponse(user.userId().value(), user.username(), user.displayName());
    }
}
