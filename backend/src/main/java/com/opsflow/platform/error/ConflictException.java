/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/**
 * The resource's current state conflicts with the request: reloading and retrying may succeed.
 *
 * <p>Use for optimistic-lock failures, uniqueness violations and illegal state transitions —
 * not for business rules, which are {@link BusinessRuleException} (422). See {@link ErrorCode}.
 */
public non-sealed class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(requireConflict(errorCode), message);
    }

    public ConflictException(ErrorCode errorCode, String message, Map<String, Object> context) {
        super(requireConflict(errorCode), message, context);
    }

    private static ErrorCode requireConflict(ErrorCode errorCode) {
        if (errorCode.status() != 409) {
            throw new IllegalArgumentException("ConflictException requires a 409 ErrorCode, got " + errorCode);
        }
        return errorCode;
    }
}
