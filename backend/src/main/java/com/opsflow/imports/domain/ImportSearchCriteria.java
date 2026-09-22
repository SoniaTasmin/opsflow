/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.util.Set;

/**
 * @param visibleOrganizations null when the caller may see every import; empty is a different
 *     answer and means only their own
 */
public record ImportSearchCriteria(
        Set<OrganizationId> visibleOrganizations,
        UserId onlyInitiatedBy,
        PeriodId reportingPeriodId,
        ImportStatus status,
        int page,
        int size) {}
