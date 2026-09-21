/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.UserDirectory;
import com.opsflow.identity.UserId;
import com.opsflow.identity.UserSummary;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.domain.StatusTransition;
import com.opsflow.submission.domain.SubmissionRepository;
import com.opsflow.submission.domain.SubmissionSearchCriteria;
import com.opsflow.submission.domain.SubmissionView;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionQueryService {

    private final SubmissionRepository submissions;
    private final OrganizationDirectory organizations;
    private final PeriodQuery periods;
    private final UserDirectory users;
    private final SubmissionAuthorization authorization;

    public SubmissionQueryService(
            SubmissionRepository submissions,
            OrganizationDirectory organizations,
            PeriodQuery periods,
            UserDirectory users,
            SubmissionAuthorization authorization) {
        this.submissions = submissions;
        this.organizations = organizations;
        this.periods = periods;
        this.users = users;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Page search(
            OrganizationId organizationId, PeriodId reportingPeriodId, SubmissionStatus status, int page, int size) {
        Actor actor = authorization.actor();

        // Scoping happens in the query, not after it: filtering a page in memory would return
        // short pages and a total that counts rows the caller may not see.
        SubmissionRepository.SubmissionPage result = submissions.search(new SubmissionSearchCriteria(
                authorization.visibleOrganizations(actor), organizationId, reportingPeriodId, status, page, size));

        Map<OrganizationId, OrganizationSummary> organizationsById = organizationsOf(result.content());
        Map<PeriodId, PeriodSummary> periodsById = periodsOf(result.content());

        List<SubmissionListItem> items = result.content().stream()
                .map(view -> new SubmissionListItem(
                        view, organizationsById.get(view.organizationId()), periodsById.get(view.reportingPeriodId())))
                .toList();
        return new Page(items, result.totalElements());
    }

    @Transactional(readOnly = true)
    public SubmissionDetail detail(SubmissionId id) {
        SubmissionView view = requireVisible(id);

        Map<UserId, UserSummary> people = usersOf(view.reviewerId(), view.submittedBy(), view.decidedBy());
        return new SubmissionDetail(
                view,
                organization(view.organizationId()),
                period(view.reportingPeriodId()),
                periods.scopeOf(view.reportingPeriodId()),
                submissions.valuesOf(id),
                lookup(people, view.reviewerId()),
                lookup(people, view.submittedBy()),
                lookup(people, view.decidedBy()));
    }

    @Transactional(readOnly = true)
    public List<TransitionEntry> history(SubmissionId id) {
        requireVisible(id);

        var transitions = submissions.historyOf(id);
        Map<UserId, UserSummary> people =
                usersOf(transitions.stream().map(StatusTransition::actorId).toArray(UserId[]::new));

        return transitions.stream()
                .map(transition ->
                        new TransitionEntry(transition, Optional.ofNullable(people.get(transition.actorId()))))
                .toList();
    }

    private SubmissionView requireVisible(SubmissionId id) {
        Actor actor = authorization.actor();
        SubmissionView view = submissions.viewById(id).orElseThrow(() -> NotFoundException.of("Submission", id));
        authorization.requireVisibility(actor, id, view.organizationId());
        return view;
    }

    private Map<OrganizationId, OrganizationSummary> organizationsOf(List<SubmissionView> views) {
        Set<OrganizationId> ids =
                views.stream().map(SubmissionView::organizationId).collect(Collectors.toCollection(LinkedHashSet::new));
        return organizations.findAllById(ids).stream()
                .collect(Collectors.toMap(OrganizationSummary::id, Function.identity()));
    }

    private Map<PeriodId, PeriodSummary> periodsOf(List<SubmissionView> views) {
        Map<PeriodId, PeriodSummary> resolved = new LinkedHashMap<>();
        views.stream().map(SubmissionView::reportingPeriodId).distinct().forEach(id -> periods.findById(id)
                .ifPresent(summary -> resolved.put(id, summary)));
        return resolved;
    }

    /** A submission with no reviewer yet has a null identifier, and an immutable map rejects one. */
    private static Optional<UserSummary> lookup(Map<UserId, UserSummary> people, UserId userId) {
        return userId == null ? Optional.empty() : Optional.ofNullable(people.get(userId));
    }

    private Map<UserId, UserSummary> usersOf(UserId... userIds) {
        Set<UserId> ids =
                Arrays.stream(userIds).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(UserSummary::userId, Function.identity()));
    }

    private OrganizationSummary organization(OrganizationId id) {
        return organizations.findById(id).orElseThrow(() -> NotFoundException.of("Organization", id));
    }

    private PeriodSummary period(PeriodId id) {
        return periods.findById(id).orElseThrow(() -> NotFoundException.of("Period", id));
    }

    public record Page(List<SubmissionListItem> content, long totalElements) {}
}
