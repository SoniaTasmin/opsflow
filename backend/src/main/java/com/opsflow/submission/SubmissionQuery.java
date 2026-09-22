/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.util.Map;
import java.util.Set;

/** Bulk by design: summarising a period is one query for the set, not one per organization. */
public interface SubmissionQuery {

    /**
     * Organizations with no submission are absent rather than zero: only the caller knows its own
     * denominator, and so only it can tell "has not started" from "not included".
     */
    Map<SubmissionStatus, Integer> countByStatus(PeriodId reportingPeriodId, Set<OrganizationId> organizationIds);
}
