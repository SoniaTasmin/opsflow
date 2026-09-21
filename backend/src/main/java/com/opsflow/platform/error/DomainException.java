/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base type for every error the application raises deliberately.
 *
 * <p>The domain throws these and nothing else: no bare {@code IllegalArgumentException} for a
 * business rule, and no {@code ResponseStatusException} (which would put HTTP concerns in the
 * domain). Every instance carries an {@link ErrorCode}, so there is no free-text-only failure
 * that a client cannot react to programmatically.
 *
 * <p>The hierarchy is sealed: the set of error categories is a closed design decision, and
 * {@code GlobalExceptionHandler} maps all of them.
 *
 * <p>{@code context} carries structured detail for the problem response — identifiers, the
 * offending value, the states involved. It must never contain secrets or personal data,
 * because it is serialised to the client.
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
