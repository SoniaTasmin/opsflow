/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.application;

import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.submission.SubmissionStatus;
import java.util.Map;

/**
 * @param participatingOrganizations the denominator: the frozen participant set narrowed to what
 *     the caller may see, not the organizations that exist today
 * @param byStatus every status, NOT_STARTED included, summing to participatingOrganizations
 */
public record PeriodSummary(
        PeriodId periodId,
        PeriodCode periodCode,
        PeriodStatus periodStatus,
        SummaryScope scope,
        int participatingOrganizations,
        Map<SummaryStatus, Integer> byStatus,
        double completionRatio) {

    public enum SummaryScope {
        ALL,
        MY_ORGANIZATIONS
    }

    public enum SummaryStatus {
        NOT_STARTED,
        DRAFT,
        SUBMITTED,
        UNDER_REVIEW,
        REJECTED,
        APPROVED;

        static SummaryStatus of(SubmissionStatus status) {
            return valueOf(status.name());
        }
    }
}
