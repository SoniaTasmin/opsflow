/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.organization.OrganizationSummary;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.submission.domain.SubmissionView;

public record SubmissionListItem(SubmissionView submission, OrganizationSummary organization, PeriodSummary period) {}
