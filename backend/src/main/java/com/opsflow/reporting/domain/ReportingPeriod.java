/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Both scopes freeze at DRAFT -> OPEN. Participation has to be frozen or historical completion
 * figures change whenever an organization is later created or archived.
 */
public final class ReportingPeriod {

    private final PeriodId id;
    private final PeriodCode code;
    private final PeriodType periodType;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private LocalDate submissionDeadline;
    private PeriodStatus status;
    private Instant openedAt;
    private Instant closedAt;
    private final Set<PeriodMetric> metricScope;
    private final Set<OrganizationId> participants;
    private final long version;

    private ReportingPeriod(
            PeriodId id,
            PeriodCode code,
            PeriodType periodType,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate submissionDeadline,
            PeriodStatus status,
            Instant openedAt,
            Instant closedAt,
            Set<PeriodMetric> metricScope,
            Set<OrganizationId> participants,
            long version) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.code = Objects.requireNonNull(code, "code is required");
        this.periodType = Objects.requireNonNull(periodType, "periodType is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.metricScope = new LinkedHashSet<>(metricScope == null ? Set.of() : metricScope);
        this.participants = new LinkedHashSet<>(participants == null ? Set.of() : participants);
        this.version = version;
        applyDates(startsOn, endsOn, submissionDeadline);
    }

    public static ReportingPeriod create(
            PeriodId id,
            PeriodCode code,
            PeriodType periodType,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate submissionDeadline) {
        return new ReportingPeriod(
                id,
                code,
                periodType,
                startsOn,
                endsOn,
                submissionDeadline,
                PeriodStatus.DRAFT,
                null,
                null,
                Set.of(),
                Set.of(),
                0L);
    }

    public static ReportingPeriod rehydrate(
            PeriodId id,
            PeriodCode code,
            PeriodType periodType,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate submissionDeadline,
            PeriodStatus status,
            Instant openedAt,
            Instant closedAt,
            Set<PeriodMetric> metricScope,
            Set<OrganizationId> participants,
            long version) {
        return new ReportingPeriod(
                id,
                code,
                periodType,
                startsOn,
                endsOn,
                submissionDeadline,
                status,
                openedAt,
                closedAt,
                metricScope,
                participants,
                version);
    }

    public void reschedule(LocalDate newStartsOn, LocalDate newEndsOn, LocalDate newDeadline) {
        assertDraft("reschedule");
        applyDates(newStartsOn, newEndsOn, newDeadline);
    }

    public void replaceMetricScope(List<PeriodMetric> metrics) {
        assertScopeEditable();
        Objects.requireNonNull(metrics, "metrics is required");

        Set<MetricId> distinct = new LinkedHashSet<>();
        metrics.forEach(metric -> {
            if (!distinct.add(metric.metricId())) {
                throw new BusinessRuleException(
                        "DUPLICATE_METRIC_IN_SCOPE",
                        "A metric may appear in a period's scope only once",
                        Map.of("metricId", metric.metricId().toString()));
            }
        });

        metricScope.clear();
        metricScope.addAll(metrics);
    }

    public void replaceParticipants(Set<OrganizationId> organizations) {
        assertScopeEditable();
        Objects.requireNonNull(organizations, "organizations is required");
        participants.clear();
        participants.addAll(organizations);
    }

    public void open(Instant now) {
        if (status != PeriodStatus.DRAFT) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a draft period can be opened",
                    Map.of("periodId", id.toString(), "status", status.name()));
        }
        if (metricScope.isEmpty() || participants.isEmpty()) {
            throw new BusinessRuleException(
                    "PERIOD_SCOPE_INCOMPLETE",
                    "A period needs at least one metric and one participating organization before it can open",
                    Map.of(
                            "periodId", id.toString(),
                            "metricCount", metricScope.size(),
                            "organizationCount", participants.size()));
        }
        this.status = PeriodStatus.OPEN;
        this.openedAt = Objects.requireNonNull(now, "now is required");
    }

    public void close(Instant now) {
        if (status != PeriodStatus.OPEN) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an open period can be closed",
                    Map.of("periodId", id.toString(), "status", status.name()));
        }
        this.status = PeriodStatus.CLOSED;
        this.closedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean isDraft() {
        return status == PeriodStatus.DRAFT;
    }

    public boolean isOpen() {
        return status == PeriodStatus.OPEN;
    }

    public boolean hasMetric(MetricId metricId) {
        return metricScope.stream().anyMatch(metric -> metric.metricId().equals(metricId));
    }

    public boolean hasParticipant(OrganizationId organizationId) {
        return participants.contains(organizationId);
    }

    private void assertScopeEditable() {
        if (status != PeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                    "PERIOD_SCOPE_FROZEN",
                    "A period's scope is frozen once it opens",
                    Map.of("periodId", id.toString(), "status", status.name()));
        }
    }

    private void assertDraft(String action) {
        if (status != PeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                    "PERIOD_NOT_DRAFT",
                    "Cannot " + action + " a period that is no longer a draft",
                    Map.of("periodId", id.toString(), "status", status.name()));
        }
    }

    private void applyDates(LocalDate newStartsOn, LocalDate newEndsOn, LocalDate newDeadline) {
        Objects.requireNonNull(newStartsOn, "startsOn is required");
        Objects.requireNonNull(newEndsOn, "endsOn is required");
        Objects.requireNonNull(newDeadline, "submissionDeadline is required");

        if (!newStartsOn.isBefore(newEndsOn)) {
            throw new BusinessRuleException(
                    "PERIOD_DATES_INVALID",
                    "A period must start before it ends",
                    Map.of("startsOn", newStartsOn.toString(), "endsOn", newEndsOn.toString()));
        }
        if (newDeadline.isBefore(newEndsOn)) {
            throw new BusinessRuleException(
                    "PERIOD_DEADLINE_INVALID",
                    "The submission deadline cannot fall before the period ends",
                    Map.of("endsOn", newEndsOn.toString(), "submissionDeadline", newDeadline.toString()));
        }
        this.startsOn = newStartsOn;
        this.endsOn = newEndsOn;
        this.submissionDeadline = newDeadline;
    }

    public PeriodId id() {
        return id;
    }

    public PeriodCode code() {
        return code;
    }

    public PeriodType periodType() {
        return periodType;
    }

    public LocalDate startsOn() {
        return startsOn;
    }

    public LocalDate endsOn() {
        return endsOn;
    }

    public LocalDate submissionDeadline() {
        return submissionDeadline;
    }

    public PeriodStatus status() {
        return status;
    }

    public Optional<Instant> openedAt() {
        return Optional.ofNullable(openedAt);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Set<PeriodMetric> metricScope() {
        return Collections.unmodifiableSet(metricScope);
    }

    public Set<OrganizationId> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public long version() {
        return version;
    }
}
