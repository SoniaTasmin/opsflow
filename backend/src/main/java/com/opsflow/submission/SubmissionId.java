/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.platform.domain.UuidV7;
import java.time.Clock;
import java.util.UUID;

public record SubmissionId(UUID value) {

    public SubmissionId {
        if (value == null) {
            throw new IllegalArgumentException("SubmissionId value is required");
        }
    }

    public static SubmissionId generate(Clock clock) {
        return new SubmissionId(UuidV7.generate(clock));
    }

    public static SubmissionId of(UUID value) {
        return new SubmissionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
