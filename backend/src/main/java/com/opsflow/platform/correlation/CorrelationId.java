/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The identifier that ties every log line, error response and background job belonging to one
 * logical operation together.
 *
 * <p>A client-supplied value is accepted so a caller can correlate across systems, but it is
 * validated first: the value is written into every log line for the request, so accepting an
 * arbitrary string would let a caller inject newlines into the log stream or bloat it without
 * bound.
 */
public record CorrelationId(String value) {

    /**
     * Permitted client-supplied form. Deliberately narrow: alphanumerics, hyphen and underscore
     * only, which covers UUIDs and ULIDs while excluding anything that could break a log line.
     */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    public CorrelationId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid correlation id");
        }
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    /** Accepts a client-supplied value, falling back to a fresh one when absent or invalid. */
    public static CorrelationId acceptOrGenerate(String candidate) {
        if (candidate == null || !VALID.matcher(candidate).matches()) {
            return generate();
        }
        return new CorrelationId(candidate);
    }

    /** The correlation id for the current thread, if one has been established. */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    public String toString() {
        return value;
    }
}
