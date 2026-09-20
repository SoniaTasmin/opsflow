/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/**
 * The caller can see the resource but may not perform this action.
 *
 * <p>Reserved for exactly that case. When the caller has no visibility of the resource at all,
 * throw {@link NotFoundException} instead.
 */
public final class AccessDeniedDomainException extends DomainException {

    public AccessDeniedDomainException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public AccessDeniedDomainException(String message, Map<String, Object> context) {
        super(ErrorCode.ACCESS_DENIED, message, context);
    }
}
