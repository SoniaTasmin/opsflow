/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/**
 * A resource does not exist — or exists but is invisible to the caller.
 *
 * <p>Both cases deliberately produce the same 404. Returning 403 for the second would confirm
 * that a resource with that identifier exists, which is exactly what an attacker enumerating
 * identifiers wants to learn. The real reason is logged with the correlation ID so support can
 * still tell the two apart.
 */
public final class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public NotFoundException(String message, Map<String, Object> context) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, context);
    }

    /** Convenience for the overwhelmingly common "type plus identifier" case. */
    public static NotFoundException of(String resourceType, Object id) {
        return new NotFoundException(
                resourceType + " not found", Map.of("resourceType", resourceType, "id", String.valueOf(id)));
    }
}
