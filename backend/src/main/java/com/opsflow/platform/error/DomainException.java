/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only failure type the domain throws. {@code context} is serialised to the client, so it
 * must never carry secrets or personal data.
 */
public abstract sealed class DomainException extends RuntimeException
        permits AccessDeniedDomainException,
                BusinessRuleException,
                ConflictException,
                MalformedRequestException,
                NotFoundException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> context;

    protected DomainException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    protected DomainException(ErrorCode errorCode, String message, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> context() {
        return context;
    }
}
