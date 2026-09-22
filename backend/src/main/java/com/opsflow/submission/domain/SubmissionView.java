/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import java.time.Instant;

public record SubmissionView(
        SubmissionId id,
        OrganizationId organizationId,
        PeriodId reportingPeriodId,
        SubmissionStatus status,
        UserId reviewerId,
        Instant submittedAt,
        UserId submittedBy,
        Instant decidedAt,
        UserId decidedBy,
        String rejectionReason,
        int valueCount,
        Instant updatedAt,
        long version) {}
