/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import java.util.Map;

/**
 * A resource does not exist, or exists and the caller may not see it. Both answer 404: a 403 for
 * the second would confirm the identifier is real. The real reason is logged.
 */
public final class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public NotFoundException(String message, Map<String, Object> context) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, context);
    }

    public static NotFoundException of(String resourceType, Object id) {
        return new NotFoundException(
                resourceType + " not found", Map.of("resourceType", resourceType, "id", String.valueOf(id)));
    }
}
