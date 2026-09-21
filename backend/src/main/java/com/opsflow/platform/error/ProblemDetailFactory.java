/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import com.opsflow.platform.correlation.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the single response shape every error in the system uses (RFC 9457).
 *
 * <p>Centralised so the contract cannot drift between handlers: every field that clients and
 * support rely on — {@code code}, {@code correlationId}, {@code timestamp} — is added in exactly
 * one place.
 */
@Component
public class ProblemDetailFactory {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailFactory.class);

    private static final Set<String> RESERVED_PROPERTIES =
            Set.of("type", "title", "status", "detail", "instance", "code", "timestamp", "correlationId", "errors");

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(ErrorCode errorCode, String detail) {
        return create(errorCode, errorCode.name(), detail, Map.of(), List.of());
    }

    public ProblemDetail create(ErrorCode errorCode, String detail, Map<String, Object> context) {
        return create(errorCode, errorCode.name(), detail, context, List.of());
    }

    /**
     * @param code the machine-readable code reported to the client. Usually the {@link ErrorCode}
     *     constant name, but a {@link BusinessRuleException} supplies its own specific rule code.
     */
    public ProblemDetail create(
            ErrorCode errorCode,
            String code,
            String detail,
            Map<String, Object> context,
            List<ApiFieldError> fieldErrors) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(errorCode.status()), detail);
        problem.setType(java.net.URI.create(errorCode.typeUri()));
        problem.setTitle(errorCode.title());

        // Context first, reserved fields last: a context key such as "code" would otherwise
        // overwrite the machine-readable error code.
        context.forEach((key, value) -> {
            if (RESERVED_PROPERTIES.contains(key)) {
                log.warn("Ignoring error context entry '{}': it would overwrite a reserved problem field", key);
                return;
            }
            problem.setProperty(key, value);
        });

        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now(clock).toString());
        CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));

        if (!fieldErrors.isEmpty()) {
            problem.setProperty("errors", fieldErrors);
        }
        return problem;
    }
}
