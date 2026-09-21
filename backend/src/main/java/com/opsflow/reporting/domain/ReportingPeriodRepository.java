/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ScopedMetric;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReportingPeriodRepository {

    Optional<ReportingPeriod> findById(PeriodId id);

    boolean existsByCode(PeriodCode code);

    ReportingPeriod insert(ReportingPeriod period, UserId createdBy);

    ReportingPeriod update(ReportingPeriod period, long expectedVersion, UserId updatedBy);

    Optional<PeriodView> viewById(PeriodId id);

    Optional<PeriodView> viewByCode(PeriodCode code);

    PeriodPage search(PeriodStatus status, PeriodType type, int page, int size);

    List<ScopedMetric> scopeOf(PeriodId id);

    Set<OrganizationId> participantsOf(PeriodId id);

    boolean isParticipant(PeriodId id, OrganizationId organizationId);

    record PeriodPage(List<PeriodView> content, long totalElements) {}
}
