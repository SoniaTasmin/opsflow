/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

/**
 * Not the OIDC {@code sub}: keying on that hands ownership of every foreign key to an external
 * service, and changing provider becomes a rewrite (ADR-0003).
 */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId value is required");
        }
    }

    public static UserId generate(Clock clock) {
        return new UserId(UuidV7.generate(clock));
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
