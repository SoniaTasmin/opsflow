/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization;

public record OrganizationSummary(OrganizationId id, OrganizationCode code, String name, boolean active) {}
