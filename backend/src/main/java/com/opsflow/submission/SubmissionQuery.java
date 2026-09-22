/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.util.Map;
import java.util.Set;

/**
 * Published reads over submission data, for callers that may not reach the aggregate.
 *
 * <p>Deliberately bulk: a caller summarising a period asks once for the whole set rather than
 * once per organization, which is the difference between one query and five hundred.
 */
public interface SubmissionQuery {

    /**
     * How many of the given organizations sit in each status for this period. Organizations with
     * no submission are absent rather than zero — the caller knows its own denominator, and only
     * it can tell "has not started" from "not included".
     */
    Map<SubmissionStatus, Integer> countByStatus(PeriodId reportingPeriodId, Set<OrganizationId> organizationIds);
}
