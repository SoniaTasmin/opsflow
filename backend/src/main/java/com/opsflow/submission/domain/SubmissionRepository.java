/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    Map<SubmissionStatus, Integer> countByStatus(PeriodId reportingPeriodId, Set<OrganizationId> organizationIds);

    record SubmissionPage(List<SubmissionView> content, long totalElements) {}
}
