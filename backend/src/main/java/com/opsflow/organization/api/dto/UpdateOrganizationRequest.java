/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** The code is not updatable: historical imports reference it. */
public record UpdateOrganizationRequest(
        @NotBlank @Size(min = 2, max = 120) String name, @PositiveOrZero long version) {}
