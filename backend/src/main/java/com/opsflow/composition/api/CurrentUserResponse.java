/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.api;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        List<String> roles,
        List<MembershipResponse> organizations) {

    public record MembershipResponse(UUID id, String code, String name, boolean active) {}
}
