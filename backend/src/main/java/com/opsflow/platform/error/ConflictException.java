/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/** State conflicts with the request and a reload may fix it: optimistic locks, uniqueness, transitions. */
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
