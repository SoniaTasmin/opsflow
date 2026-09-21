/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

public record OrganizationId(UUID value) {

    public OrganizationId {
        if (value == null) {
            throw new IllegalArgumentException("OrganizationId value is required");
        }
    }

    public static OrganizationId generate(Clock clock) {
        return new OrganizationId(UuidV7.generate(clock));
    }

    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
