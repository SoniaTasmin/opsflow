/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank
                @Pattern(
                        regexp = "^[A-Z0-9_-]{2,32}$",
                        message = "must be 2-32 characters of A-Z, 0-9, underscore or hyphen")
                String code,
        @NotBlank @Size(min = 2, max = 120) String name) {}
