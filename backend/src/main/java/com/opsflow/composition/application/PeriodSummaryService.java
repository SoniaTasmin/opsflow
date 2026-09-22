/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.application;

import com.opsflow.composition.application.PeriodSummary.SummaryScope;
import com.opsflow.composition.application.PeriodSummary.SummaryStatus;
import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.submission.SubmissionQuery;
import com.opsflow.submission.SubmissionStatus;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spans three modules — the period's frozen participants, the caller's memberships and the
 * submissions themselves — so it lives in the composition layer rather than making any of them
 * depend on another's internals.
 *
 * <p>Two published calls and no joins: the participant set comes from reporting, the counts come
 * from submission for exactly that set, and NOT_STARTED is the difference between them.
 */
@Service
public class PeriodSummaryService {

    private final PeriodQuery periods;
    private final SubmissionQuery submissions;
    private final OrganizationAccess access;
    private final CurrentActorProvider currentActor;

    public PeriodSummaryService(
            PeriodQuery periods,
            SubmissionQuery submissions,
            OrganizationAccess access,
            CurrentActorProvider currentActor) {
        this.periods = periods;
        this.submissions = submissions;
        this.access = access;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    public PeriodSummary of(PeriodId periodId) {
        var period = periods.findById(periodId).orElseThrow(() -> NotFoundException.of("Period", periodId));

        Actor actor = currentActor.requireActor();
        boolean seesEverything = actor.isAdmin() || actor.hasRole(Role.REVIEWER);
        Set<OrganizationId> participants = visibleParticipants(periodId, actor, seesEverything);

        Map<SubmissionStatus, Integer> counts = submissions.countByStatus(periodId, participants);
        Map<SummaryStatus, Integer> byStatus = new EnumMap<>(SummaryStatus.class);
        for (SummaryStatus status : SummaryStatus.values()) {
            byStatus.put(status, 0);
        }
        int withSubmissions = 0;
        for (var entry : counts.entrySet()) {
            byStatus.put(SummaryStatus.of(entry.getKey()), entry.getValue());
            withSubmissions += entry.getValue();
        }
        // A participant with no submission row has not started. Every submission belongs to a
        // participant, so this can never go negative.
        byStatus.put(SummaryStatus.NOT_STARTED, participants.size() - withSubmissions);

        int approved = byStatus.get(SummaryStatus.APPROVED);
        double completion = participants.isEmpty() ? 0d : (double) approved / participants.size();

        return new PeriodSummary(
                period.id(),
                period.code(),
                period.status(),
                seesEverything ? SummaryScope.ALL : SummaryScope.MY_ORGANIZATIONS,
                participants.size(),
                byStatus,
                completion);
    }

    /**
     * The frozen participant set, narrowed by membership for a caller who may not see everything.
     * Narrowing the denominator as well as the counts is what keeps them reconcilable.
     */
    private Set<OrganizationId> visibleParticipants(PeriodId periodId, Actor actor, boolean seesEverything) {
        Set<OrganizationId> participants = periods.participantsOf(periodId);
        if (seesEverything) {
            return participants;
        }
        Set<OrganizationId> mine = access.organizationIdsOf(actor.userId());
        Set<OrganizationId> visible = new LinkedHashSet<>(participants);
        visible.retainAll(mine);
        return visible;
    }
}
