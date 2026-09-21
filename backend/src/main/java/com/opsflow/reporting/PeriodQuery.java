/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import com.opsflow.organization.OrganizationId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PeriodQuery {

    Optional<PeriodSummary> findById(PeriodId id);

    Optional<PeriodSummary> findByCode(PeriodCode code);

    boolean isOpen(PeriodId id);

    List<ScopedMetric> scopeOf(PeriodId id);

    Set<OrganizationId> participantsOf(PeriodId id);

    boolean isParticipant(PeriodId id, OrganizationId organizationId);
}
