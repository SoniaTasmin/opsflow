/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

/**
 * The machine-readable half of the error contract.
 *
 * <p>The {@code code} a client receives is this constant's name. Clients switch on it, so
 * <strong>renaming a constant is a breaking API change</strong> while rewording the
 * human-readable title or detail is not.
 *
 * <p>The HTTP status is carried as a plain {@code int} rather than Spring's {@code HttpStatus}
 * so that this enum — which domain exceptions across every module reference — stays free of
 * framework types.
 *
 * <h2>Choosing between 409 and 422</h2>
 *
 * This distinction gets re-litigated in review unless it is written down once:
 *
 * <ul>
 *   <li><b>409 Conflict</b> — the resource's current state conflicts with the request.
 *       Reloading and retrying may succeed. Optimistic-lock failures, illegal state
 *       transitions, uniqueness violations.
 *   <li><b>422 Unprocessable Content</b> — the request is well formed but a business rule
 *       rejects it. Retrying unchanged will always fail.
 * </ul>
 */
public enum ErrorCode {

    // --- 400: the request itself is malformed -------------------------------
    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),

    // --- 401 / 403 ----------------------------------------------------------
    UNAUTHENTICATED(401, "Authentication required"),
    ACCESS_DENIED(403, "Access denied"),

    // --- 404 ----------------------------------------------------------------
    // Also returned when a resource exists but the caller cannot see its owning
    // organization: a 403 would confirm that the resource exists.
    RESOURCE_NOT_FOUND(404, "Resource not found"),

    // --- 409: state and concurrency conflicts -------------------------------
    VERSION_CONFLICT(409, "Version conflict"),
    INVALID_STATE_TRANSITION(409, "Invalid state transition"),
    DUPLICATE_RESOURCE(409, "Duplicate resource"),

    // --- 422: well formed, but a business rule rejects it --------------------
    // Modules contribute their own specific rule codes (e.g. REQUIRED_METRICS_MISSING)
    // through BusinessRuleException; this is the fallback when none is given.
    BUSINESS_RULE_VIOLATION(422, "Business rule violation"),

    // --- 415 / 413 ----------------------------------------------------------
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    PAYLOAD_TOO_LARGE(413, "Payload too large"),

    // --- 500 / 503 ----------------------------------------------------------
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

    /** Human-readable summary. Safe to reword; unlike the constant name, it is not a contract. */
    public String title() {
        return title;
    }

    /**
     * The {@code type} URI identifying this problem kind, as RFC 9457 recommends.
     *
     * <p>A URN rather than an HTTP URL. RFC 9457 does not require the URI to be
     * dereferenceable, and publishing {@code https://} URIs for a domain we do not own and do
     * not serve documentation from would be a promise the project cannot keep — clients (and
     * their tooling) reasonably expect an HTTP type URI to resolve.
     *
     * <p>{@code code} remains the primary machine-readable contract; this identifies the
     * problem kind stably without implying a published, fetchable definition.
     */
    public String typeUri() {
        return "urn:opsflow:problem:" + name().toLowerCase().replace('_', '-');
    }
}
