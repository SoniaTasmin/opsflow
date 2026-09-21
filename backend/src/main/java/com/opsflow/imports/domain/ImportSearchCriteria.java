/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.util.Set;

/**
 * @param visibleOrganizations null when the caller may see every import; an empty set means they
 *     may see only their own unscoped ones, which is a different answer
 * @param onlyInitiatedBy set when the caller may see nothing beyond what they started themselves
 */
public record ImportSearchCriteria(
        Set<OrganizationId> visibleOrganizations,
        UserId onlyInitiatedBy,
        PeriodId reportingPeriodId,
        ImportStatus status,
        int page,
        int size) {}
