/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED) String username,
        @Schema(requiredMode = RequiredMode.REQUIRED) String displayName,
        String email,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<String> roles,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<MembershipResponse> organizations) {

    /** Carries no role: membership has none. */
    public record MembershipResponse(
            @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean active) {}
}
