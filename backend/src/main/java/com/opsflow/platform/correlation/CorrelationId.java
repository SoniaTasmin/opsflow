/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Ties every log line, error response and background job of one operation together. A
 * client-supplied value is validated first: it reaches every log line for the request.
 */
public record CorrelationId(String value) {

    // Narrow on purpose: covers UUIDs and ULIDs, excludes anything that could break a log line.
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    public CorrelationId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid correlation id");
        }
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    public static CorrelationId acceptOrGenerate(String candidate) {
        if (candidate == null || !VALID.matcher(candidate).matches()) {
            return generate();
        }
        return new CorrelationId(candidate);
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    public String toString() {
        return value;
    }
}
