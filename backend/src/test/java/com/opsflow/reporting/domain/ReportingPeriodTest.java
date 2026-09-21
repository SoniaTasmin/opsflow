/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportingPeriodTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final Instant NOW = Instant.parse("2026-04-01T00:00:00Z");
    private static final LocalDate STARTS = LocalDate.of(2026, 1, 1);
    private static final LocalDate ENDS = LocalDate.of(2026, 3, 31);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 4, 15);

    private static ReportingPeriod draft() {
        return ReportingPeriod.create(
                PeriodId.generate(CLOCK), new PeriodCode("2026-Q1"), PeriodType.QUARTER, STARTS, ENDS, DEADLINE);
    }

    private static PeriodMetric metric() {
        return new PeriodMetric(MetricId.generate(CLOCK), true, 0);
    }

    private static ReportingPeriod opened() {
        ReportingPeriod period = draft();
        period.replaceMetricScope(List.of(metric()));
        period.replaceParticipants(Set.of(OrganizationId.generate(CLOCK)));
        period.open(NOW);
        return period;
    }

    @Test
    void aNewPeriodIsADraftWithNoScope() {
        ReportingPeriod period = draft();

        assertThat(period.status()).isEqualTo(PeriodStatus.DRAFT);
        assertThat(period.metricScope()).isEmpty();
        assertThat(period.participants()).isEmpty();
    }

    @Test
    void rejectsAPeriodThatDoesNotStartBeforeItEnds() {
        assertThatThrownBy(() -> ReportingPeriod.create(
                        PeriodId.generate(CLOCK), new PeriodCode("BAD1"), PeriodType.MONTH, ENDS, STARTS, DEADLINE))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsADeadlineBeforeThePeriodEnds() {
        assertThatThrownBy(() -> ReportingPeriod.create(
                        PeriodId.generate(CLOCK),
                        new PeriodCode("BAD2"),
                        PeriodType.MONTH,
                        STARTS,
                        ENDS,
                        ENDS.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotOpenWithoutAMetricScope() {
        ReportingPeriod period = draft();
        period.replaceParticipants(Set.of(OrganizationId.generate(CLOCK)));

        assertThatThrownBy(() -> period.open(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("PERIOD_SCOPE_INCOMPLETE"));
    }

    @Test
    void cannotOpenWithoutParticipants() {
        ReportingPeriod period = draft();
        period.replaceMetricScope(List.of(metric()));

        assertThatThrownBy(() -> period.open(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("PERIOD_SCOPE_INCOMPLETE"));
    }

    @Test
    void openingFreezesBothScopes() {
        ReportingPeriod period = opened();

        assertThatThrownBy(() -> period.replaceMetricScope(List.of(metric())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("PERIOD_SCOPE_FROZEN"));

        assertThatThrownBy(() -> period.replaceParticipants(Set.of(OrganizationId.generate(CLOCK))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("PERIOD_SCOPE_FROZEN"));
    }

    @Test
    void aClosedPeriodsScopeStaysFrozen() {
        ReportingPeriod period = opened();
        period.close(NOW.plusSeconds(60));

        assertThatThrownBy(() -> period.replaceMetricScope(List.of(metric())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotRescheduleOnceOpen() {
        ReportingPeriod period = opened();

        assertThatThrownBy(() -> period.reschedule(STARTS, ENDS.plusDays(1), DEADLINE.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("PERIOD_NOT_DRAFT"));
    }

    @Test
    void cannotOpenTwice() {
        ReportingPeriod period = opened();

        assertThatThrownBy(() -> period.open(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotCloseADraft() {
        assertThatThrownBy(() -> draft().close(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsTheSameMetricTwiceInScope() {
        ReportingPeriod period = draft();
        PeriodMetric duplicate = metric();

        assertThatThrownBy(() ->
                        period.replaceMetricScope(List.of(duplicate, new PeriodMetric(duplicate.metricId(), false, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("DUPLICATE_METRIC_IN_SCOPE"));
    }

    @Test
    void scopesAreNotMutableFromOutside() {
        ReportingPeriod period = opened();

        assertThatThrownBy(() -> period.participants().add(OrganizationId.generate(CLOCK)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> period.metricScope().add(metric())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordsWhenItOpenedAndClosed() {
        ReportingPeriod period = opened();
        period.close(NOW.plusSeconds(3600));

        assertThat(period.openedAt()).contains(NOW);
        assertThat(period.closedAt()).contains(NOW.plusSeconds(3600));
        assertThat(period.status()).isEqualTo(PeriodStatus.CLOSED);
    }
}
