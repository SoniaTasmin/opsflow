/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

public record MetricId(UUID value) {

    public MetricId {
        if (value == null) {
            throw new IllegalArgumentException("MetricId value is required");
        }
    }

    public static MetricId generate(Clock clock) {
        return new MetricId(UuidV7.generate(clock));
    }

    public static MetricId of(UUID value) {
        return new MetricId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
