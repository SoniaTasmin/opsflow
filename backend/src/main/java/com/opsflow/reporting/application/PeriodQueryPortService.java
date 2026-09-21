/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.application;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.reporting.domain.PeriodView;
import com.opsflow.reporting.domain.ReportingPeriodRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PeriodQueryPortService implements PeriodQuery {

    private final ReportingPeriodRepository periods;

    PeriodQueryPortService(ReportingPeriodRepository periods) {
        this.periods = periods;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PeriodSummary> findById(PeriodId id) {
        return periods.viewById(id).map(PeriodQueryPortService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PeriodSummary> findByCode(PeriodCode code) {
        return periods.viewByCode(code).map(PeriodQueryPortService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOpen(PeriodId id) {
        return periods.viewById(id)
                .map(view -> view.status() == com.opsflow.reporting.PeriodStatus.OPEN)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScopedMetric> scopeOf(PeriodId id) {
        return periods.scopeOf(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<OrganizationId> participantsOf(PeriodId id) {
        return periods.participantsOf(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(PeriodId id, OrganizationId organizationId) {
        return periods.isParticipant(id, organizationId);
    }

    private static PeriodSummary toSummary(PeriodView view) {
        return new PeriodSummary(
                view.id(),
                view.code(),
                view.periodType(),
                view.startsOn(),
                view.endsOn(),
                view.submissionDeadline(),
                view.status());
    }
}
