/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

public record ImportJobId(UUID value) {

    public ImportJobId {
        if (value == null) {
            throw new IllegalArgumentException("ImportJobId value is required");
        }
    }

    public static ImportJobId generate(Clock clock) {
        return new ImportJobId(UuidV7.generate(clock));
    }

    public static ImportJobId of(UUID value) {
        return new ImportJobId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
