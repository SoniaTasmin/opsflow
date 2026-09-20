/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security configuration, bound and validated at startup.
 *
 * <p>Nothing here has a permissive default. A missing audience or an empty CORS origin list must
 * fail startup loudly rather than quietly produce an application that accepts tokens meant for
 * something else, or requests from anywhere.
 *
 * @param audience the value this API requires in a token's {@code aud} claim
 * @param allowedOrigins exact origins permitted to call the API from a browser
 */
@ConfigurationProperties(prefix = "opsflow.security")
@Validated
public record SecurityProperties(@NotEmpty String audience, @NotEmpty List<String> allowedOrigins) {}
