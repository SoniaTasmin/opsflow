/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/**
 * The HTTP request itself is incomplete or malformed — a required header is absent, a part is
 * missing. Maps to 400.
 *
 * <p>Distinct from {@link BusinessRuleException}: that one is a well-formed request a rule
 * refuses, and no amount of correcting the request would change its answer. Carries its own code
 * for the same reason business rules do — a client needs to know which requirement it missed.
 */
public final class MalformedRequestException extends DomainException {

    private final String code;

    public MalformedRequestException(String code, String message) {
        this(code, message, Map.of());
    }

    public MalformedRequestException(String code, String message, Map<String, Object> context) {
        super(ErrorCode.MALFORMED_REQUEST, message, context);
        this.code = code;
    }

    /** What the client switches on, in {@code SCREAMING_SNAKE_CASE}. */
    public String code() {
        return code;
    }
}
