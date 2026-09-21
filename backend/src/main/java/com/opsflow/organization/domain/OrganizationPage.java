/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import java.util.List;

public record OrganizationPage(List<OrganizationView> content, long totalElements) {}
