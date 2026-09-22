/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/** The HTTP request is incomplete — a missing header or part: 400, not a business rule. */
public final class MalformedRequestException extends DomainException {

    private final String code;

    public MalformedRequestException(String code, String message) {
        this(code, message, Map.of());
    }

    public MalformedRequestException(String code, String message, Map<String, Object> context) {
        super(ErrorCode.MALFORMED_REQUEST, message, context);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
