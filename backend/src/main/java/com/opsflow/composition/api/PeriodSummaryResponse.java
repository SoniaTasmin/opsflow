/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.api;

import com.opsflow.composition.application.PeriodSummary;
import com.opsflow.composition.application.PeriodSummary.SummaryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/**
 * @param participatingOrganizations the period's frozen participant set as this caller sees it —
 *     not the number of organizations that exist today
 * @param completionRatio approved submissions over participating organizations, 0 when there are
 *     none to report
 */
public record PeriodSummaryResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED) UUID periodId,
        @Schema(requiredMode = RequiredMode.REQUIRED) String periodCode,
        @Schema(requiredMode = RequiredMode.REQUIRED) String periodStatus,
        @Schema(requiredMode = RequiredMode.REQUIRED) String scope,
        @Schema(requiredMode = RequiredMode.REQUIRED) int participatingOrganizations,
        @Schema(requiredMode = RequiredMode.REQUIRED) int notStarted,
        @Schema(requiredMode = RequiredMode.REQUIRED) int draft,
        @Schema(requiredMode = RequiredMode.REQUIRED) int submitted,
        @Schema(requiredMode = RequiredMode.REQUIRED) int underReview,
        @Schema(requiredMode = RequiredMode.REQUIRED) int rejected,
        @Schema(requiredMode = RequiredMode.REQUIRED) int approved,
        @Schema(requiredMode = RequiredMode.REQUIRED) double completionRatio) {

    public static PeriodSummaryResponse from(PeriodSummary summary) {
        return new PeriodSummaryResponse(
                summary.periodId().value(),
                summary.periodCode().value(),
                summary.periodStatus().name(),
                summary.scope().name(),
                summary.participatingOrganizations(),
                count(summary, SummaryStatus.NOT_STARTED),
                count(summary, SummaryStatus.DRAFT),
                count(summary, SummaryStatus.SUBMITTED),
                count(summary, SummaryStatus.UNDER_REVIEW),
                count(summary, SummaryStatus.REJECTED),
                count(summary, SummaryStatus.APPROVED),
                summary.completionRatio());
    }

    private static int count(PeriodSummary summary, SummaryStatus status) {
        return summary.byStatus().getOrDefault(status, 0);
    }
}
