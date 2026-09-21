/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record VersionedRequest(@PositiveOrZero long version) {}
