/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.composition.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.composition.application.PeriodSummary.SummaryScope;
import com.opsflow.composition.application.PeriodSummary.SummaryStatus;
import com.opsflow.identity.Actor;
import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.domain.Role;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.submission.SubmissionQuery;
import com.opsflow.submission.SubmissionStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The composition rules, with the modules stubbed: what the denominator is, who sees what, and
 * that the whole set is counted in one call rather than one call per organization.
 */
class PeriodSummaryServiceTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final PeriodId PERIOD = PeriodId.generate(CLOCK);
    private static final UserId USER = UserId.generate(CLOCK);

    private final OrganizationId alpha = OrganizationId.generate(CLOCK);
    private final OrganizationId beta = OrganizationId.generate(CLOCK);
    private final OrganizationId gamma = OrganizationId.generate(CLOCK);

    private final StubPeriods periods = new StubPeriods();
    private final StubSubmissions submissions = new StubSubmissions();
    private final StubAccess access = new StubAccess();

    private PeriodSummary summarise(Role... roles) {
        Actor actor = new Actor(USER, Set.of(roles));
        return new PeriodSummaryService(periods, submissions, access, () -> actor).of(PERIOD);
    }

    @Test
    void theFrozenParticipantSetIsTheDenominator() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta, gamma));
        submissions.counts = Map.of(SubmissionStatus.APPROVED, 1);

        PeriodSummary summary = summarise(Role.ADMIN);

        assertThat(summary.participatingOrganizations()).isEqualTo(3);
        assertThat(summary.scope()).isEqualTo(SummaryScope.ALL);
    }

    @Test
    void participantsWithNoSubmissionCountAsNotStarted() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta, gamma));
        submissions.counts = Map.of(SubmissionStatus.DRAFT, 1);

        PeriodSummary summary = summarise(Role.ADMIN);

        assertThat(summary.byStatus().get(SummaryStatus.NOT_STARTED)).isEqualTo(2);
        assertThat(summary.byStatus().get(SummaryStatus.DRAFT)).isEqualTo(1);
    }

    @Test
    void everyStatusIsPresentEvenWhenNobodyIsInIt() {
        periods.participants = new LinkedHashSet<>(List.of(alpha));
        submissions.counts = Map.of();

        PeriodSummary summary = summarise(Role.ADMIN);

        assertThat(summary.byStatus()).containsOnlyKeys(SummaryStatus.values());
        assertThat(summary.byStatus().get(SummaryStatus.APPROVED)).isZero();
    }

    @Test
    void theCountsAlwaysAddUpToTheParticipants() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta, gamma));
        submissions.counts = Map.of(
                SubmissionStatus.DRAFT, 1,
                SubmissionStatus.UNDER_REVIEW, 1);

        PeriodSummary summary = summarise(Role.ADMIN);

        assertThat(summary.byStatus().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .isEqualTo(summary.participatingOrganizations());
    }

    @Test
    void completionIsApprovedOverParticipants() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta, gamma, OrganizationId.generate(CLOCK)));
        submissions.counts = Map.of(SubmissionStatus.APPROVED, 1);

        assertThat(summarise(Role.ADMIN).completionRatio()).isEqualTo(0.25);
    }

    @Test
    void aPeriodNobodyReportsInIsNotADivisionByZero() {
        periods.participants = new LinkedHashSet<>();
        submissions.counts = Map.of();

        PeriodSummary summary = summarise(Role.ADMIN);

        assertThat(summary.completionRatio()).isZero();
        assertThat(summary.participatingOrganizations()).isZero();
    }

    // ------------------------------------------------------------ visibility

    @Test
    void aReporterSeesOnlyTheirOwnOrganizationsIncludingTheDenominator() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta, gamma));
        access.memberships = Set.of(beta);
        submissions.counts = Map.of(SubmissionStatus.APPROVED, 1);

        PeriodSummary summary = summarise(Role.USER);

        assertThat(summary.scope()).isEqualTo(SummaryScope.MY_ORGANIZATIONS);
        assertThat(summary.participatingOrganizations()).isEqualTo(1);
        assertThat(submissions.askedAbout).containsExactly(beta);
    }

    @Test
    void aReviewerSeesEveryParticipant() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta));
        access.memberships = Set.of();

        assertThat(summarise(Role.REVIEWER).scope()).isEqualTo(SummaryScope.ALL);
    }

    @Test
    void aMemberOfNoParticipatingOrganizationSeesAnEmptySummaryRatherThanEveryones() {
        periods.participants = new LinkedHashSet<>(List.of(alpha, beta));
        access.memberships = Set.of(gamma);

        PeriodSummary summary = summarise(Role.USER);

        assertThat(summary.participatingOrganizations()).isZero();
        assertThat(submissions.askedAbout).isEmpty();
    }

    @Test
    void anUnknownPeriodIsNotFound() {
        periods.known = false;

        assertThatThrownBy(() -> summarise(Role.ADMIN)).isInstanceOf(NotFoundException.class);
    }

    /** The whole point of the published contract: one call for the set, not one per member. */
    @Test
    void theSetIsCountedInASingleCall() {
        periods.participants = new LinkedHashSet<>();
        for (int i = 0; i < 250; i++) {
            periods.participants.add(OrganizationId.generate(CLOCK));
        }
        submissions.counts = Map.of(SubmissionStatus.APPROVED, 100);

        summarise(Role.ADMIN);

        assertThat(submissions.calls).isEqualTo(1);
    }

    // ------------------------------------------------------------ stubs

    private static final class StubPeriods implements PeriodQuery {
        private Set<OrganizationId> participants = new LinkedHashSet<>();
        private boolean known = true;

        @Override
        public Optional<com.opsflow.reporting.PeriodSummary> findById(PeriodId id) {
            return known
                    ? Optional.of(new com.opsflow.reporting.PeriodSummary(
                            PERIOD,
                            new PeriodCode("2026-Q1"),
                            PeriodType.QUARTER,
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 3, 31),
                            LocalDate.of(2026, 4, 15),
                            PeriodStatus.OPEN))
                    : Optional.empty();
        }

        @Override
        public Optional<com.opsflow.reporting.PeriodSummary> findByCode(PeriodCode code) {
            return findById(PERIOD);
        }

        @Override
        public boolean isOpen(PeriodId id) {
            return true;
        }

        @Override
        public List<ScopedMetric> scopeOf(PeriodId id) {
            return List.of();
        }

        @Override
        public Set<OrganizationId> participantsOf(PeriodId id) {
            return participants;
        }

        @Override
        public boolean isParticipant(PeriodId id, OrganizationId organizationId) {
            return participants.contains(organizationId);
        }
    }

    private static final class StubSubmissions implements SubmissionQuery {
        private Map<SubmissionStatus, Integer> counts = Map.of();
        private Set<OrganizationId> askedAbout = Set.of();
        private int calls;

        @Override
        public Map<SubmissionStatus, Integer> countByStatus(
                PeriodId reportingPeriodId, Set<OrganizationId> organizationIds) {
            calls++;
            askedAbout = organizationIds;
            return organizationIds.isEmpty() ? Map.of() : counts;
        }
    }

    private static final class StubAccess implements OrganizationAccess {
        private Set<OrganizationId> memberships = Set.of();

        @Override
        public boolean isMember(UserId userId, OrganizationId organizationId) {
            return memberships.contains(organizationId);
        }

        @Override
        public Set<OrganizationId> organizationIdsOf(UserId userId) {
            return memberships;
        }
    }
}
