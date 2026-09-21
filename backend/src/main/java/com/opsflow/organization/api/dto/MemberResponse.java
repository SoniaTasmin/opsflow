/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api.dto;

import com.opsflow.identity.UserSummary;
import java.util.UUID;

public record MemberResponse(UUID userId, String username, String displayName) {

    public static MemberResponse from(UserSummary user) {
        return new MemberResponse(user.userId().value(), user.username(), user.displayName());
    }
}
