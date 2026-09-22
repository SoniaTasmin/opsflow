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

/** One read for the detail screen: without the period's metric scope the value form cannot render. */
public record SubmissionDetail(
        SubmissionView submission,
        OrganizationSummary organization,
        PeriodSummary period,
        List<ScopedMetric> scope,
        List<SubmissionValue> values,
        Optional<UserSummary> reviewer,
        Optional<UserSummary> submittedBy,
        Optional<UserSummary> decidedBy) {}
