/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.web;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * The version a client last read, echoed back so a concurrent change is rejected rather than
 * silently overwritten. Shared rather than repeated per module: it carries no business meaning,
 * and three identically named copies collapse into one OpenAPI schema anyway.
 */
public record VersionedRequest(@PositiveOrZero long version) {}
