/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * No permissive defaults: a missing audience or an empty origin list fails startup rather than
 * quietly accepting tokens meant for something else, or requests from anywhere.
 */
@ConfigurationProperties(prefix = "opsflow.security")
@Validated
public record SecurityProperties(@NotEmpty String audience, @NotEmpty List<String> allowedOrigins) {}
