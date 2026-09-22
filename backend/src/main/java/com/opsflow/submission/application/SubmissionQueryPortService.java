/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionQuery;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.domain.SubmissionRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The published read port. Authorization is the caller's: it decides which organizations it may
 * ask about, and this answers only for those.
 */
@Service
class SubmissionQueryPortService implements SubmissionQuery {

    private final SubmissionRepository submissions;

    SubmissionQueryPortService(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<SubmissionStatus, Integer> countByStatus(
            PeriodId reportingPeriodId, Set<OrganizationId> organizationIds) {
        return submissions.countByStatus(reportingPeriodId, organizationIds);
    }
}
