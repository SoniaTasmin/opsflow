/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.identity.UserSummary;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.submission.domain.SubmissionValue;
import com.opsflow.submission.domain.SubmissionView;
import java.util.List;
import java.util.Optional;

/**
 * Everything the detail screen needs in one read: the submission, who it belongs to, and the
 * period's metric scope — without which the value form cannot be rendered at all.
 */
public record SubmissionDetail(
        SubmissionView submission,
        OrganizationSummary organization,
        PeriodSummary period,
        List<ScopedMetric> scope,
        List<SubmissionValue> values,
        Optional<UserSummary> reviewer,
        Optional<UserSummary> submittedBy,
        Optional<UserSummary> decidedBy) {}
