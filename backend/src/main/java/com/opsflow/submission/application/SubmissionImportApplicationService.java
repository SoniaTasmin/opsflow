/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.submission.ApplyValuesCommand;
import com.opsflow.submission.ApplyValuesResult;
import com.opsflow.submission.ImportAuthority;
import com.opsflow.submission.ImportedValue;
import com.opsflow.submission.RejectedValue;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionImportService;
import com.opsflow.submission.ValueSource;
import com.opsflow.submission.domain.Submission;
import com.opsflow.submission.domain.SubmissionRepository;
import com.opsflow.submission.domain.SubmissionValidationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imported values take the route typed ones take. Only the grouping differs, which is why this
 * takes a whole organization's values at once.
 */
@Service
class SubmissionImportApplicationService implements SubmissionImportService {

    private final SubmissionRepository submissions;
    private final OrganizationAccess access;
    private final OrganizationDirectory organizations;
    private final PeriodQuery periods;
    private final Clock clock;

    SubmissionImportApplicationService(
            SubmissionRepository submissions,
            OrganizationAccess access,
            OrganizationDirectory organizations,
            PeriodQuery periods,
            Clock clock) {
        this.submissions = submissions;
        this.access = access;
        this.organizations = organizations;
        this.periods = periods;
        this.clock = clock;
    }

    /** Joins the chunk transaction, so a chunk rollback undoes the aggregate saves it made. */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ApplyValuesResult applyValues(ApplyValuesCommand command) {
        ImportAuthority authority = command.authority();

        // Re-checked per group against live data: revoking a membership mid-import stops the
        // remaining rows for that organization.
        if (!authority.waivesMembership() && !access.isMember(authority.initiatedBy(), command.organizationId())) {
            return ApplyValuesResult.rejectedGroup(
                    "ORG_NOT_PERMITTED", "The importing user does not belong to this organization");
        }

        Optional<OrganizationSummary> organization = organizations.findById(command.organizationId());
        if (organization.isEmpty()) {
            return ApplyValuesResult.rejectedGroup("ORG_UNKNOWN", "This organization no longer exists");
        }
        if (!organization.get().active()) {
            return ApplyValuesResult.rejectedGroup("ORG_ARCHIVED", "This organization is archived");
        }

        Optional<PeriodSummary> period = periods.findById(command.reportingPeriodId());
        if (period.isEmpty()) {
            return ApplyValuesResult.rejectedGroup("PERIOD_UNKNOWN", "This reporting period no longer exists");
        }
        if (!periods.isParticipant(command.reportingPeriodId(), command.organizationId())) {
            return ApplyValuesResult.rejectedGroup(
                    "ORG_NOT_IN_PERIOD_SCOPE", "This organization does not report in this period");
        }
        if (!period.get().isOpen()) {
            return ApplyValuesResult.rejectedGroup("PERIOD_NOT_OPEN", "This reporting period is not open");
        }

        Optional<Submission> existing =
                submissions.findByOrganizationAndPeriod(command.organizationId(), command.reportingPeriodId());
        if (existing.isPresent() && !existing.get().status().editable()) {
            return ApplyValuesResult.rejectedGroup(
                    "SUBMISSION_NOT_EDITABLE",
                    "This submission is " + existing.get().status() + " and cannot be changed");
        }

        Submission submission = existing.orElseGet(() -> Submission.create(
                SubmissionId.generate(clock),
                command.organizationId(),
                command.reportingPeriodId(),
                authority.initiatedBy(),
                Instant.now(clock)));

        SubmissionValidationPolicy policy =
                new SubmissionValidationPolicy(periods.scopeOf(command.reportingPeriodId()));
        List<RejectedValue> rejected = new ArrayList<>();
        int applied = 0;
        for (ImportedValue value : command.values()) {
            try {
                submission.setValue(value.metricId(), value.value(), value.note(), ValueSource.IMPORT, policy);
                applied++;
            } catch (BusinessRuleException rejection) {
                rejected.add(new RejectedValue(value.rowNumber(), rejection.ruleCode(), rejection.getMessage()));
            }
        }

        if (applied > 0) {
            if (existing.isPresent()) {
                submissions.update(submission, submission.version(), authority.initiatedBy());
            } else {
                submissions.insert(submission, authority.initiatedBy());
            }
        }

        // An import never changes status, so it never appends a transition.
        return new ApplyValuesResult(submission.id(), applied, rejected, null);
    }
}
