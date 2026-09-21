/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionStatus;
import java.util.Set;

/**
 * @param visibleOrganizations null when the caller may see every organization; an empty set
 *     therefore means "sees nothing", which is a different answer and must not be confused with
 *     it — conflating the two is how a scoping filter turns into a data leak
 */
public record SubmissionSearchCriteria(
        Set<OrganizationId> visibleOrganizations,
        OrganizationId organizationId,
        PeriodId reportingPeriodId,
        SubmissionStatus status,
        int page,
        int size) {}
