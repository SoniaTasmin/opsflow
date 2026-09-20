/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

/**
 * The application's own identifier for a person.
 *
 * <p>Deliberately not the OIDC {@code sub}. The specification permits a subject to be any string
 * up to 255 characters, and using it as a primary key hands ownership of every foreign key in the
 * system to an external service — making a change of identity provider a rewrite rather than a
 * configuration change (ADR-0003).
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
