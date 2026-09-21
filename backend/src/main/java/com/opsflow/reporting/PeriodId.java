/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

public record PeriodId(UUID value) {

    public PeriodId {
        if (value == null) {
            throw new IllegalArgumentException("PeriodId value is required");
        }
    }

    public static PeriodId generate(Clock clock) {
        return new PeriodId(UuidV7.generate(clock));
    }

    public static PeriodId of(UUID value) {
        return new PeriodId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
