/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.error.AccessDeniedDomainException;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.ValueSource;
import com.opsflow.submission.domain.Submission;
import com.opsflow.submission.domain.Submission.ValueEntry;
import com.opsflow.submission.domain.SubmissionRepository;
import com.opsflow.submission.domain.SubmissionValidationPolicy;
import com.opsflow.submission.domain.SubmissionView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every state change a submission can undergo.
 *
 * <p>The aggregate owns the state machine and the values; this owns the rules that need another
 * aggregate — the period being open, the organization participating — and the organization-scoped
 * authorization that cannot be decided until the submission has been read.
 */
@Service
@EnableConfigurationProperties(WorkflowProperties.class)
public class SubmissionCommandService {

    private final SubmissionRepository submissions;
    private final PeriodQuery periods;
    private final OrganizationDirectory organizations;
    private final SubmissionAuthorization authorization;
    private final WorkflowProperties workflow;
    private final Clock clock;

    public SubmissionCommandService(
            SubmissionRepository submissions,
            PeriodQuery periods,
            OrganizationDirectory organizations,
            SubmissionAuthorization authorization,
            WorkflowProperties workflow,
            Clock clock) {
        this.submissions = submissions;
        this.periods = periods;
        this.organizations = organizations;
        this.authorization = authorization;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Transactional
    public SubmissionView create(OrganizationId organizationId, PeriodId reportingPeriodId) {
        Actor actor = authorization.actor();
        authorization.requireAuthoring(actor, organizationId);

        PeriodSummary period = requirePeriod(reportingPeriodId);
        requireOpen(period);
        requireActiveOrganization(organizationId);
        requireParticipant(period, organizationId);

        Submission submission = Submission.create(
                SubmissionId.generate(clock), organizationId, reportingPeriodId, actor.userId(), Instant.now(clock));
        submissions.insert(submission, actor.userId());
        return submissions.viewById(submission.id()).orElseThrow();
    }

    @Transactional
    public SubmissionView saveValues(SubmissionId id, List<ValueEntry> entries, long expectedVersion) {
        return authoring(id, expectedVersion, (actor, submission) -> {
            PeriodSummary period = requirePeriod(submission.reportingPeriodId());
            requireOpen(period);
            submission.replaceValues(entries, ValueSource.MANUAL, policyFor(submission));
        });
    }

    @Transactional
    public SubmissionView submit(SubmissionId id, long expectedVersion) {
        return authoring(id, expectedVersion, (actor, submission) -> {
            PeriodSummary period = requirePeriod(submission.reportingPeriodId());
            requireOpen(period);
            submission.submit(policyFor(submission), actor.userId(), Instant.now(clock));
        });
    }

    @Transactional
    public SubmissionView withdraw(SubmissionId id, long expectedVersion) {
        return authoring(
                id, expectedVersion, (actor, submission) -> submission.withdraw(actor.userId(), Instant.now(clock)));
    }

    // A review may be finished after the period closes -- the deadline governs authors, not
    // reviewers, and a closed period must not leave work stuck in the queue forever.
    @Transactional
    public SubmissionView claim(SubmissionId id, long expectedVersion) {
        return reviewing(
                id,
                expectedVersion,
                false,
                (actor, submission) -> submission.claim(actor.userId(), Instant.now(clock)));
    }

    @Transactional
    public SubmissionView release(SubmissionId id, long expectedVersion) {
        return reviewing(
                id,
                expectedVersion,
                true,
                (actor, submission) -> submission.release(actor.userId(), Instant.now(clock)));
    }

    @Transactional
    public SubmissionView approve(SubmissionId id, long expectedVersion) {
        return reviewing(
                id,
                expectedVersion,
                true,
                (actor, submission) ->
                        submission.approve(actor.userId(), Instant.now(clock), workflow.allowSelfApproval()));
    }

    @Transactional
    public SubmissionView reject(SubmissionId id, String reason, long expectedVersion) {
        return reviewing(
                id,
                expectedVersion,
                true,
                (actor, submission) -> submission.reject(actor.userId(), reason, Instant.now(clock)));
    }

    private SubmissionView authoring(SubmissionId id, long expectedVersion, BiConsumer<Actor, Submission> command) {
        Actor actor = authorization.actor();
        Submission submission = load(id);
        authorization.requireVisibility(actor, id, submission.organizationId());
        authorization.requireAuthoring(actor, submission.organizationId());

        command.accept(actor, submission);
        submissions.update(submission, expectedVersion, actor.userId());
        return submissions.viewById(id).orElseThrow();
    }

    private SubmissionView reviewing(
            SubmissionId id, long expectedVersion, boolean mustHoldTheClaim, BiConsumer<Actor, Submission> command) {
        Actor actor = authorization.actor();
        Submission submission = load(id);
        authorization.requireVisibility(actor, id, submission.organizationId());
        if (mustHoldTheClaim) {
            requireTheClaim(actor, submission);
        }

        command.accept(actor, submission);
        submissions.update(submission, expectedVersion, actor.userId());
        return submissions.viewById(id).orElseThrow();
    }

    /**
     * Reviews are held, not shared: whoever claimed it decides it. An administrator is allowed
     * through so a review left claimed by someone unavailable can still be released.
     */
    private static void requireTheClaim(Actor actor, Submission submission) {
        UserId reviewer = submission.reviewerId().orElse(null);
        if (reviewer == null || reviewer.equals(actor.userId()) || actor.isAdmin()) {
            return;
        }
        throw new AccessDeniedDomainException(
                "This submission is being reviewed by someone else",
                Map.of("submissionId", submission.id().toString()));
    }

    private SubmissionValidationPolicy policyFor(Submission submission) {
        return new SubmissionValidationPolicy(periods.scopeOf(submission.reportingPeriodId()));
    }

    private Submission load(SubmissionId id) {
        return submissions.findById(id).orElseThrow(() -> NotFoundException.of("Submission", id));
    }

    private PeriodSummary requirePeriod(PeriodId reportingPeriodId) {
        return periods.findById(reportingPeriodId).orElseThrow(() -> NotFoundException.of("Period", reportingPeriodId));
    }

    private static void requireOpen(PeriodSummary period) {
        if (!period.isOpen()) {
            throw new BusinessRuleException(
                    "PERIOD_NOT_OPEN",
                    "This reporting period is not open for submissions",
                    Map.of(
                            "reportingPeriodId",
                            period.id().toString(),
                            "status",
                            period.status().name()));
        }
    }

    private void requireParticipant(PeriodSummary period, OrganizationId organizationId) {
        if (!periods.isParticipant(period.id(), organizationId)) {
            throw new BusinessRuleException(
                    "ORGANIZATION_NOT_IN_PERIOD_SCOPE",
                    "This organization does not report in this period",
                    Map.of(
                            "organizationId", organizationId.toString(),
                            "reportingPeriodId", period.id().toString()));
        }
    }

    private void requireActiveOrganization(OrganizationId organizationId) {
        OrganizationSummary organization = organizations
                .findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId));
        if (!organization.active()) {
            throw new BusinessRuleException(
                    "ORGANIZATION_NOT_ACTIVE",
                    "An archived organization cannot report",
                    Map.of("organizationId", organizationId.toString()));
        }
    }
}
