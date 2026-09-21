/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api.dto;

import com.opsflow.identity.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

public record MemberResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID userId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String username,
        @Schema(requiredMode = RequiredMode.REQUIRED) String displayName) {

    public static MemberResponse from(UserSummary user) {
        return new MemberResponse(user.userId().value(), user.username(), user.displayName());
    }
}
