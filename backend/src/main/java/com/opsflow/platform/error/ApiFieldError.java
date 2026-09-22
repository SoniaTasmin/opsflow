/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param rejectedValue left null by the generic validation handler, which cannot know whether a
 *     field holds a credential. Only a handler that knows the value is safe may set it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(String field, String code, String message, Object rejectedValue) {

    public static ApiFieldError of(String field, String code, String message) {
        return new ApiFieldError(field, code, message, null);
    }
}
