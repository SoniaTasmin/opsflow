/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.api;

import com.opsflow.identity.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

public record UserResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) String username,
        @Schema(requiredMode = RequiredMode.REQUIRED) String displayName) {

    public static UserResponse from(UserSummary user) {
        return new UserResponse(user.userId().value(), user.username(), user.displayName());
    }
}
