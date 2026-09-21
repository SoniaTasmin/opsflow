/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.application;

import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.reporting.domain.PeriodView;
import com.opsflow.reporting.domain.ReportingPeriodRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodQueryService {

    private final ReportingPeriodRepository periods;
    private final OrganizationDirectory organizations;

    public PeriodQueryService(ReportingPeriodRepository periods, OrganizationDirectory organizations) {
        this.periods = periods;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public ReportingPeriodRepository.PeriodPage search(PeriodStatus status, PeriodType type, int page, int size) {
        return periods.search(status, type, page, size);
    }

    @Transactional(readOnly = true)
    public PeriodView require(PeriodId id) {
        return periods.viewById(id).orElseThrow(() -> NotFoundException.of("Period", id));
    }

    @Transactional(readOnly = true)
    public List<ScopedMetric> scopeOf(PeriodId id) {
        return periods.scopeOf(id);
    }

    @Transactional(readOnly = true)
    public List<OrganizationSummary> participantsOf(PeriodId id) {
        return organizations.findAllById(periods.participantsOf(id));
    }
}
