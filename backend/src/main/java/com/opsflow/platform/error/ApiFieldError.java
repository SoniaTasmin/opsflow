/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One field-level problem within a failed request.
 *
 * @param field the path to the offending field, e.g. {@code values[3].amount}
 * @param code a stable, machine-readable reason such as {@code NOT_NULL} or {@code MIN_VALUE}
 * @param message human-readable explanation
 * @param rejectedValue the submitted value. Null by default and omitted from the response:
 *     the generic validation handler never populates it, because it cannot know whether a
 *     field holds a credential or personal data. Only a domain-specific handler that knows
 *     the value is a safe scalar may set it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(String field, String code, String message, Object rejectedValue) {

    public static ApiFieldError of(String field, String code, String message) {
        return new ApiFieldError(field, code, message, null);
    }
}
