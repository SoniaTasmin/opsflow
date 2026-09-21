/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import static com.opsflow.submission.domain.SubmissionFixtures.AUTHOR;
import static com.opsflow.submission.domain.SubmissionFixtures.CLOCK;
import static com.opsflow.submission.domain.SubmissionFixtures.HEADCOUNT;
import static com.opsflow.submission.domain.SubmissionFixtures.NOW;
import static com.opsflow.submission.domain.SubmissionFixtures.REVENUE;
import static com.opsflow.submission.domain.SubmissionFixtures.REVIEWER;
import static com.opsflow.submission.domain.SubmissionFixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.identity.UserId;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.ValueSource;
import com.opsflow.submission.domain.Submission.ValueEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SubmissionTest {

    @Test
    void aNewSubmissionIsADraftAndRecordsItsCreation() {
        Submission submission = SubmissionFixtures.draft();

        assertThat(submission.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(submission.values()).isEmpty();
        assertThat(submission.pendingTransitions()).singleElement().satisfies(transition -> {
            assertThat(transition.from()).isNull();
            assertThat(transition.to()).isEqualTo(SubmissionStatus.DRAFT);
            assertThat(transition.actorId()).isEqualTo(AUTHOR);
        });
    }

    // The whole point of the state machine is the pairs it refuses, so the refusals are
    // enumerated rather than sampled: a command silently becoming legal in a new state is
    // exactly the regression a happy-path test cannot see.
    @ParameterizedTest(name = "{1} is refused in {0}")
    @MethodSource("refusedCommands")
    void refusesCommandsThatTheStateDoesNotAllow(SubmissionStatus status, String command) {
        Submission submission = SubmissionFixtures.inState(status);

        assertThatThrownBy(() -> invoke(submission, command)).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest(name = "{1} is allowed in {0}")
    @MethodSource("allowedCommands")
    void allowsTheCommandsTheStateDefines(SubmissionStatus status, String command) {
        Submission submission = SubmissionFixtures.inState(status);

        assertThatCode(() -> invoke(submission, command)).doesNotThrowAnyException();
    }

    private static Stream<Arguments> allowedCommands() {
        return Stream.of(
                Arguments.of(SubmissionStatus.DRAFT, "submit"),
                Arguments.of(SubmissionStatus.SUBMITTED, "withdraw"),
                Arguments.of(SubmissionStatus.SUBMITTED, "claim"),
                Arguments.of(SubmissionStatus.UNDER_REVIEW, "release"),
                Arguments.of(SubmissionStatus.UNDER_REVIEW, "approve"),
                Arguments.of(SubmissionStatus.UNDER_REVIEW, "reject"),
                Arguments.of(SubmissionStatus.REJECTED, "submit"));
    }

    private static Stream<Arguments> refusedCommands() {
        List<String> commands = List.of("submit", "withdraw", "claim", "release", "approve", "reject");
        List<Arguments> allowed = allowedCommands().toList();
        return Stream.of(SubmissionStatus.values()).flatMap(status -> commands.stream()
                .filter(command ->
                        allowed.stream().noneMatch(pair -> pair.get()[0] == status && pair.get()[1].equals(command)))
                .map(command -> Arguments.of(status, command)));
    }

    private static void invoke(Submission submission, String command) {
        switch (command) {
            case "submit" -> submission.submit(policy(), AUTHOR, NOW);
            case "withdraw" -> submission.withdraw(AUTHOR, NOW);
            case "claim" -> submission.claim(REVIEWER, NOW);
            case "release" -> submission.release(REVIEWER, NOW);
            case "approve" -> submission.approve(REVIEWER, NOW, false);
            case "reject" -> submission.reject(REVIEWER, "Needs correcting", NOW);
            default -> throw new IllegalArgumentException(command);
        }
    }

    @Test
    void cannotBeSubmittedWhileARequiredMetricHasNoValue() {
        Submission submission = SubmissionFixtures.draft();
        submission.setValue(REVENUE, new BigDecimal("12.50"), null, ValueSource.MANUAL, policy());

        assertThatThrownBy(() -> submission.submit(policy(), AUTHOR, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("REQUIRED_METRICS_MISSING");
    }

    @Test
    void theSubmitterCannotApproveTheirOwnSubmission() {
        Submission submission = SubmissionFixtures.submitted();
        submission.claim(AUTHOR, NOW);

        assertThatThrownBy(() -> submission.approve(AUTHOR, NOW, false))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void selfApprovalIsPossibleOnlyWhereItHasBeenDeliberatelyEnabled() {
        Submission submission = SubmissionFixtures.submitted();
        submission.claim(AUTHOR, NOW);

        submission.approve(AUTHOR, NOW, true);

        assertThat(submission.status()).isEqualTo(SubmissionStatus.APPROVED);
    }

    @ParameterizedTest
    @EnumSource(
            value = SubmissionStatus.class,
            names = {"SUBMITTED", "UNDER_REVIEW", "APPROVED"})
    void valuesCannotBeChangedOnceTheSubmissionHasLeftTheAuthor(SubmissionStatus status) {
        Submission submission = SubmissionFixtures.inState(status);

        assertThatThrownBy(
                        () -> submission.setValue(HEADCOUNT, new BigDecimal("1"), null, ValueSource.MANUAL, policy()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("SUBMISSION_NOT_EDITABLE");
    }

    @Test
    void aRejectedSubmissionIsEditableAgainAndKeepsTheReason() {
        Submission submission = SubmissionFixtures.rejected();

        assertThat(submission.status()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(submission.rejectionReason()).contains("The headcount does not match the payroll export");
        assertThatCode(() -> submission.setValue(HEADCOUNT, new BigDecimal("43"), null, ValueSource.MANUAL, policy()))
                .doesNotThrowAnyException();
    }

    @Test
    void resubmittingClearsTheRejection() {
        Submission submission = SubmissionFixtures.rejected();

        submission.submit(policy(), AUTHOR, NOW);

        assertThat(submission.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.rejectionReason()).isEmpty();
        assertThat(submission.decidedAt()).isEmpty();
    }

    @Test
    void aRejectionHasToSayWhy() {
        Submission submission = SubmissionFixtures.underReview();

        assertThatThrownBy(() -> submission.reject(REVIEWER, "  ", NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).ruleCode())
                .isEqualTo("REJECTION_REASON_REQUIRED");
    }

    @Test
    void withdrawingReturnsTheSubmissionToItsAuthor() {
        Submission submission = SubmissionFixtures.submitted();

        submission.withdraw(AUTHOR, NOW);

        assertThat(submission.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(submission.submittedAt()).isEmpty();
        assertThat(submission.submittedBy()).isEmpty();
    }

    @Test
    void claimingRecordsWhoHoldsTheReviewAndReleasingGivesItBack() {
        Submission submission = SubmissionFixtures.submitted();

        submission.claim(REVIEWER, NOW);
        assertThat(submission.reviewerId()).contains(REVIEWER);

        submission.release(REVIEWER, NOW);
        assertThat(submission.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.reviewerId()).isEmpty();
    }

    @Test
    void anApprovalRecordsWhoDecidedAndReleasesTheClaim() {
        Submission submission = SubmissionFixtures.approved();

        assertThat(submission.decidedBy()).contains(REVIEWER);
        assertThat(submission.decidedAt()).contains(NOW);
        assertThat(submission.reviewerId()).isEmpty();
    }

    @Test
    void everyStateChangeIsRecordedInOrder() {
        Submission submission = SubmissionFixtures.approved();

        assertThat(submission.pendingTransitions())
                .extracting(StatusTransition::to)
                .containsExactly(
                        SubmissionStatus.DRAFT,
                        SubmissionStatus.SUBMITTED,
                        SubmissionStatus.UNDER_REVIEW,
                        SubmissionStatus.APPROVED);
    }

    @Test
    void aRejectionCarriesItsReasonIntoTheHistory() {
        Submission submission = SubmissionFixtures.rejected();

        assertThat(submission.pendingTransitions()).last().satisfies(transition -> assertThat(transition.reason())
                .isEqualTo("The headcount does not match the payroll export"));
    }

    @Test
    void replacingValuesClearsTheOnesLeftOut() {
        Submission submission = SubmissionFixtures.draft();
        submission.replaceValues(
                List.of(
                        new ValueEntry(HEADCOUNT, new BigDecimal("42"), null),
                        new ValueEntry(REVENUE, new BigDecimal("1.5"), "estimate")),
                ValueSource.MANUAL,
                policy());

        submission.replaceValues(
                List.of(new ValueEntry(HEADCOUNT, new BigDecimal("43"), null)), ValueSource.MANUAL, policy());

        assertThat(submission.values()).extracting(SubmissionValue::metricId).containsExactly(HEADCOUNT);
    }

    @Test
    void refusesTwoValuesForTheSameMetric() {
        Submission submission = SubmissionFixtures.draft();

        assertThatThrownBy(() -> submission.replaceValues(
                        List.of(
                                new ValueEntry(HEADCOUNT, new BigDecimal("1"), null),
                                new ValueEntry(HEADCOUNT, new BigDecimal("2"), null)),
                        ValueSource.MANUAL,
                        policy()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rehydrationCarriesNoPendingHistory() {
        Submission stored = SubmissionFixtures.draft();
        Submission submission = Submission.rehydrate(
                stored.id(),
                stored.organizationId(),
                stored.reportingPeriodId(),
                SubmissionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                3L);

        assertThat(submission.pendingTransitions()).isEmpty();
        assertThat(submission.version()).isEqualTo(3L);
    }

    @Test
    void anyoneOtherThanTheSubmitterMayApprove() {
        Submission submission = SubmissionFixtures.submitted();
        submission.claim(REVIEWER, NOW);
        UserId somebodyElse = UserId.generate(CLOCK);

        assertThatCode(() -> submission.approve(somebodyElse, NOW, false)).doesNotThrowAnyException();
    }
}
