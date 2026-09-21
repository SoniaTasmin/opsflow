/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.ValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The only writer of submission data. Manual editing and CSV import converge here, so there is
 * no second path with weaker invariants.
 *
 * <p>Rules that need another aggregate — the period being open, the organization participating —
 * are enforced by the application service, which is where a cross-aggregate read belongs. What
 * this class owns is the state machine, the values and the history they produce.
 */
public final class Submission {

    private final SubmissionId id;
    private final OrganizationId organizationId;
    private final PeriodId reportingPeriodId;
    private SubmissionStatus status;
    private UserId reviewerId;
    private Instant submittedAt;
    private UserId submittedBy;
    private Instant decidedAt;
    private UserId decidedBy;
    private String rejectionReason;
    private final Map<MetricId, SubmissionValue> values;
    private final List<StatusTransition> pendingTransitions = new ArrayList<>();
    private final long version;

    private Submission(
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
            Collection<SubmissionValue> values,
            long version) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId is required");
        this.reportingPeriodId = Objects.requireNonNull(reportingPeriodId, "reportingPeriodId is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.reviewerId = reviewerId;
        this.submittedAt = submittedAt;
        this.submittedBy = submittedBy;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.rejectionReason = rejectionReason;
        this.values = new LinkedHashMap<>();
        if (values != null) {
            values.forEach(value -> this.values.put(value.metricId(), value));
        }
        this.version = version;
    }

    public static Submission create(
            SubmissionId id, OrganizationId organizationId, PeriodId reportingPeriodId, UserId author, Instant now) {
        Submission submission = new Submission(
                id,
                organizationId,
                reportingPeriodId,
                SubmissionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0L);
        submission.record(null, SubmissionStatus.DRAFT, author, null, now);
        return submission;
    }

    public static Submission rehydrate(
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
            Collection<SubmissionValue> values,
            long version) {
        return new Submission(
                id,
                organizationId,
                reportingPeriodId,
                status,
                reviewerId,
                submittedAt,
                submittedBy,
                decidedAt,
                decidedBy,
                rejectionReason,
                values,
                version);
    }

    /**
     * Replaces the whole value set: a metric absent from {@code entries} is removed. The value
     * form posts every metric in scope, so a partial post is a client bug rather than a partial
     * save, and silently merging one would leave the author believing they had cleared a figure.
     */
    public void replaceValues(List<ValueEntry> entries, ValueSource source, SubmissionValidationPolicy policy) {
        assertEditable();
        Objects.requireNonNull(entries, "entries is required");

        Map<MetricId, SubmissionValue> replacement = new LinkedHashMap<>();
        entries.forEach(entry -> {
            policy.validate(entry.metricId(), entry.value());
            if (replacement.put(
                            entry.metricId(),
                            new SubmissionValue(entry.metricId(), entry.value(), entry.note(), source))
                    != null) {
                throw new BusinessRuleException(
                        "DUPLICATE_METRIC_VALUE",
                        "A metric may carry only one value in a submission",
                        Map.of("metricId", entry.metricId().toString()));
            }
        });

        values.clear();
        values.putAll(replacement);
    }

    /** Upserts one value, leaving the rest alone. This is the path CSV import will use. */
    public void setValue(
            MetricId metricId, BigDecimal value, String note, ValueSource source, SubmissionValidationPolicy policy) {
        assertEditable();
        policy.validate(metricId, value);
        values.put(metricId, new SubmissionValue(metricId, value, note, source));
    }

    public void submit(SubmissionValidationPolicy policy, UserId actor, Instant now) {
        requireStatus("submit", SubmissionStatus.DRAFT, SubmissionStatus.REJECTED);

        Set<MetricId> missing = new LinkedHashSet<>(policy.requiredMetrics());
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "REQUIRED_METRICS_MISSING",
                    "Every required metric needs a value before this submission can be sent for review",
                    Map.of(
                            "missingMetricIds",
                            missing.stream()
                                    .map(policy::require)
                                    .map(metric -> metric.code().value())
                                    .toList()));
        }

        SubmissionStatus from = status;
        status = SubmissionStatus.SUBMITTED;
        submittedAt = now;
        submittedBy = actor;
        clearDecision();
        record(from, status, actor, null, now);
    }

    public void withdraw(UserId actor, Instant now) {
        requireStatus("withdraw", SubmissionStatus.SUBMITTED);
        status = SubmissionStatus.DRAFT;
        submittedAt = null;
        submittedBy = null;
        record(SubmissionStatus.SUBMITTED, status, actor, null, now);
    }

    public void claim(UserId reviewer, Instant now) {
        requireStatus("claim", SubmissionStatus.SUBMITTED);
        status = SubmissionStatus.UNDER_REVIEW;
        reviewerId = Objects.requireNonNull(reviewer, "reviewer is required");
        record(SubmissionStatus.SUBMITTED, status, reviewer, null, now);
    }

    public void release(UserId actor, Instant now) {
        requireStatus("release", SubmissionStatus.UNDER_REVIEW);
        status = SubmissionStatus.SUBMITTED;
        reviewerId = null;
        record(SubmissionStatus.UNDER_REVIEW, status, actor, null, now);
    }

    public void approve(UserId actor, Instant now, boolean allowSelfApproval) {
        requireStatus("approve", SubmissionStatus.UNDER_REVIEW);
        if (!allowSelfApproval && actor != null && actor.equals(submittedBy)) {
            throw new BusinessRuleException(
                    "SELF_APPROVAL_FORBIDDEN",
                    "The person who submitted this cannot also approve it",
                    Map.of("submissionId", id.toString()));
        }
        status = SubmissionStatus.APPROVED;
        decidedAt = now;
        decidedBy = actor;
        reviewerId = null;
        rejectionReason = null;
        record(SubmissionStatus.UNDER_REVIEW, status, actor, null, now);
    }

    public void reject(UserId actor, String reason, Instant now) {
        requireStatus("reject", SubmissionStatus.UNDER_REVIEW);
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(
                    "REJECTION_REASON_REQUIRED",
                    "A rejection has to say what needs correcting",
                    Map.of("submissionId", id.toString()));
        }
        status = SubmissionStatus.REJECTED;
        rejectionReason = reason.strip();
        decidedAt = now;
        decidedBy = actor;
        reviewerId = null;
        record(SubmissionStatus.UNDER_REVIEW, status, actor, rejectionReason, now);
    }

    public void assertEditable() {
        if (!status.editable()) {
            throw new BusinessRuleException(
                    "SUBMISSION_NOT_EDITABLE",
                    "Values can only be changed while a submission is a draft or has been rejected",
                    Map.of("submissionId", id.toString(), "status", status.name()));
        }
    }

    private void requireStatus(String command, SubmissionStatus... allowed) {
        for (SubmissionStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new ConflictException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "A submission cannot be " + command + "ed from " + status,
                Map.of(
                        "submissionId", id.toString(),
                        "status", status.name(),
                        "command", command));
    }

    private void clearDecision() {
        decidedAt = null;
        decidedBy = null;
        rejectionReason = null;
        reviewerId = null;
    }

    private void record(SubmissionStatus from, SubmissionStatus to, UserId actor, String reason, Instant now) {
        pendingTransitions.add(new StatusTransition(from, to, actor, reason, now));
    }

    public SubmissionId id() {
        return id;
    }

    public OrganizationId organizationId() {
        return organizationId;
    }

    public PeriodId reportingPeriodId() {
        return reportingPeriodId;
    }

    public SubmissionStatus status() {
        return status;
    }

    public Optional<UserId> reviewerId() {
        return Optional.ofNullable(reviewerId);
    }

    public Optional<Instant> submittedAt() {
        return Optional.ofNullable(submittedAt);
    }

    public Optional<UserId> submittedBy() {
        return Optional.ofNullable(submittedBy);
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    public Optional<UserId> decidedBy() {
        return Optional.ofNullable(decidedBy);
    }

    public Optional<String> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    public List<SubmissionValue> values() {
        return List.copyOf(values.values());
    }

    public List<StatusTransition> pendingTransitions() {
        return Collections.unmodifiableList(pendingTransitions);
    }

    public long version() {
        return version;
    }

    public record ValueEntry(MetricId metricId, BigDecimal value, String note) {

        public ValueEntry {
            if (metricId == null) {
                throw new IllegalArgumentException("metricId is required");
            }
            if (value == null) {
                throw new IllegalArgumentException("value is required");
            }
        }
    }
}
