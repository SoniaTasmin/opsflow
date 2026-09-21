/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionId;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository {

    Optional<Submission> findById(SubmissionId id);

    Optional<SubmissionView> viewById(SubmissionId id);

    Optional<SubmissionView> viewByOrganizationAndPeriod(OrganizationId organizationId, PeriodId reportingPeriodId);

    Optional<Submission> findByOrganizationAndPeriod(OrganizationId organizationId, PeriodId reportingPeriodId);

    Submission insert(Submission submission, UserId createdBy);

    Submission update(Submission submission, long expectedVersion, UserId updatedBy);

    SubmissionPage search(SubmissionSearchCriteria criteria);

    List<SubmissionValue> valuesOf(SubmissionId id);

    List<StatusTransition> historyOf(SubmissionId id);

    record SubmissionPage(List<SubmissionView> content, long totalElements) {}
}
