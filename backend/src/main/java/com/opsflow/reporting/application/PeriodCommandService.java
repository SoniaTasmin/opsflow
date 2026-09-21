/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.application;

import com.opsflow.identity.CurrentUserProvider;
import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.domain.MetricRepository;
import com.opsflow.reporting.domain.MetricView;
import com.opsflow.reporting.domain.PeriodMetric;
import com.opsflow.reporting.domain.PeriodView;
import com.opsflow.reporting.domain.ReportingPeriod;
import com.opsflow.reporting.domain.ReportingPeriodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodCommandService {

    private final ReportingPeriodRepository periods;
    private final MetricRepository metrics;
    private final OrganizationDirectory organizations;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public PeriodCommandService(
            ReportingPeriodRepository periods,
            MetricRepository metrics,
            OrganizationDirectory organizations,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.periods = periods;
        this.metrics = metrics;
        this.organizations = organizations;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public PeriodView create(
            PeriodCode code, PeriodType type, LocalDate startsOn, LocalDate endsOn, LocalDate submissionDeadline) {
        if (periods.existsByCode(code)) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "A period with this code already exists",
                    Map.of("periodCode", code.value()));
        }
        ReportingPeriod period =
                ReportingPeriod.create(PeriodId.generate(clock), code, type, startsOn, endsOn, submissionDeadline);
        periods.insert(period, actorId());
        return periods.viewById(period.id()).orElseThrow();
    }

    @Transactional
    public PeriodView reschedule(
            PeriodId id, LocalDate startsOn, LocalDate endsOn, LocalDate submissionDeadline, long expectedVersion) {
        ReportingPeriod period = load(id);
        period.reschedule(startsOn, endsOn, submissionDeadline);
        periods.update(period, expectedVersion, actorId());
        return periods.viewById(id).orElseThrow();
    }

    @Transactional
    public PeriodView replaceMetricScope(PeriodId id, List<MetricScopeEntry> entries, long expectedVersion) {
        ReportingPeriod period = load(id);
        period.replaceMetricScope(toPeriodMetrics(entries));
        periods.update(period, expectedVersion, actorId());
        return periods.viewById(id).orElseThrow();
    }

    @Transactional
    public PeriodView replaceParticipants(PeriodId id, Set<OrganizationId> organizationIds, long expectedVersion) {
        ReportingPeriod period = load(id);
        period.replaceParticipants(requireActiveOrganizations(organizationIds));
        periods.update(period, expectedVersion, actorId());
        return periods.viewById(id).orElseThrow();
    }

    @Transactional
    public PeriodView open(PeriodId id, long expectedVersion) {
        ReportingPeriod period = load(id);
        period.open(Instant.now(clock));
        periods.update(period, expectedVersion, actorId());
        return periods.viewById(id).orElseThrow();
    }

    @Transactional
    public PeriodView close(PeriodId id, long expectedVersion) {
        ReportingPeriod period = load(id);
        period.close(Instant.now(clock));
        periods.update(period, expectedVersion, actorId());
        return periods.viewById(id).orElseThrow();
    }

    private List<PeriodMetric> toPeriodMetrics(List<MetricScopeEntry> entries) {
        Set<com.opsflow.reporting.MetricId> ids =
                entries.stream().map(MetricScopeEntry::metricId).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<com.opsflow.reporting.MetricId, MetricView> found =
                metrics.viewsById(ids).stream().collect(Collectors.toMap(MetricView::id, view -> view));

        entries.forEach(entry -> {
            MetricView metric = found.get(entry.metricId());
            if (metric == null) {
                throw NotFoundException.of("Metric", entry.metricId());
            }
            if (!metric.active()) {
                throw new BusinessRuleException(
                        "METRIC_NOT_ACTIVE",
                        "An archived metric cannot be added to a period's scope",
                        Map.of(
                                "metricId",
                                entry.metricId().toString(),
                                "metricCode",
                                metric.code().value()));
            }
        });

        return entries.stream()
                .map(entry -> new PeriodMetric(entry.metricId(), entry.required(), entry.displayOrder()))
                .toList();
    }

    private Set<OrganizationId> requireActiveOrganizations(Set<OrganizationId> organizationIds) {
        Map<OrganizationId, OrganizationSummary> found = organizations.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(OrganizationSummary::id, summary -> summary));

        organizationIds.forEach(organizationId -> {
            OrganizationSummary organization = found.get(organizationId);
            if (organization == null) {
                throw NotFoundException.of("Organization", organizationId);
            }
            if (!organization.active()) {
                throw new BusinessRuleException(
                        "ORGANIZATION_NOT_ACTIVE",
                        "An archived organization cannot participate in a period",
                        Map.of(
                                "organizationId", organizationId.toString(),
                                "organizationCode", organization.code().value()));
            }
        });
        return new LinkedHashSet<>(organizationIds);
    }

    private ReportingPeriod load(PeriodId id) {
        return periods.findById(id).orElseThrow(() -> NotFoundException.of("Period", id));
    }

    private UserId actorId() {
        return currentUser.currentUserId().orElse(null);
    }

    public record MetricScopeEntry(com.opsflow.reporting.MetricId metricId, boolean required, int displayOrder) {}
}
