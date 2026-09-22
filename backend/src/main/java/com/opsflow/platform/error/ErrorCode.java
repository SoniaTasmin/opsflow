/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

/**
 * Clients switch on the constant name, so renaming one is a breaking API change; the title is
 * not part of the contract. 409 means reloading and retrying may succeed, 422 means it will
 * not (ADR-0010). The status is an {@code int} so the domain layer stays free of Spring types.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),

    UNAUTHENTICATED(401, "Authentication required"),
    ACCESS_DENIED(403, "Access denied"),

    // Also used when a resource exists but the caller cannot see its organization: a 403 there
    // would confirm the resource exists.
    RESOURCE_NOT_FOUND(404, "Resource not found"),

    VERSION_CONFLICT(409, "Version conflict"),
    INVALID_STATE_TRANSITION(409, "Invalid state transition"),
    PERIOD_OVERLAP(409, "Overlapping reporting period"),
    DUPLICATE_RESOURCE(409, "Duplicate resource"),

    // Modules supply their own rule codes through BusinessRuleException; this is the fallback.
    BUSINESS_RULE_VIOLATION(422, "Business rule violation"),

    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    PAYLOAD_TOO_LARGE(413, "Payload too large"),

    INTERNAL_ERROR(500, "Internal server error"),
    SERVICE_UNAVAILABLE(503, "Service unavailable");

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** A URN, not an https URL: RFC 9457 does not require the type to resolve, and ours would not. */
    public String typeUri() {
        return "urn:opsflow:problem:" + name().toLowerCase().replace('_', '-');
    }
}
