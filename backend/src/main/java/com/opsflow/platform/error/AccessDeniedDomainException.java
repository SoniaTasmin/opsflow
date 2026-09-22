/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/** The caller sees the resource but may not act on it. No visibility at all is {@link NotFoundException}. */
public final class AccessDeniedDomainException extends DomainException {

    public AccessDeniedDomainException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public AccessDeniedDomainException(String message, Map<String, Object> context) {
        super(ErrorCode.ACCESS_DENIED, message, context);
    }
}
