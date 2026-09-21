/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizationTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private static Organization active() {
        return Organization.create(
                OrganizationId.generate(CLOCK), new OrganizationCode("BERLIN_OPS"), "Berlin Operations");
    }

    private static Organization archived() {
        Organization organization = active();
        organization.archive(NOW);
        return organization;
    }

    @Test
    void aNewOrganizationIsActiveAndEmpty() {
        Organization organization = active();

        assertThat(organization.isActive()).isTrue();
        assertThat(organization.members()).isEmpty();
        assertThat(organization.archivedAt()).isEmpty();
    }

    @Test
    void membershipIsASetOfUsersAndNothingElse() {
        Organization organization = active();
        UserId user = UserId.generate(CLOCK);

        assertThat(organization.addMember(user)).isTrue();
        assertThat(organization.hasMember(user)).isTrue();
        assertThat(organization.members()).containsExactly(user);
    }

    @Test
    void addingAnExistingMemberIsASuccessfulNoOp() {
        Organization organization = active();
        UserId user = UserId.generate(CLOCK);
        organization.addMember(user);

        assertThat(organization.addMember(user)).isFalse();
        assertThat(organization.members()).hasSize(1);
    }

    @Test
    void removingANonMemberIsASuccessfulNoOp() {
        assertThat(active().removeMember(UserId.generate(CLOCK))).isFalse();
    }

    @Test
    void archivingRecordsWhen() {
        Organization organization = active();

        organization.archive(NOW);

        assertThat(organization.isActive()).isFalse();
        assertThat(organization.archivedAt()).contains(NOW);
    }

    @Test
    void archivingTwiceIsAConflictRatherThanASilentNoOp() {
        assertThatThrownBy(() -> archived().archive(NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already archived");
    }

    @Test
    void anArchivedOrganizationAcceptsNoNewMembers() {
        assertThatThrownBy(() -> archived().addMember(UserId.generate(CLOCK)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex ->
                        assertThat(((BusinessRuleException) ex).ruleCode()).isEqualTo("ORGANIZATION_ARCHIVED"));
    }

    @Test
    void anArchivedOrganizationCannotBeRenamed() {
        assertThatThrownBy(() -> archived().rename("New Name")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void membersCanStillBeRemovedFromAnArchivedOrganization() {
        Organization organization = active();
        UserId user = UserId.generate(CLOCK);
        organization.addMember(user);
        organization.archive(NOW);

        assertThat(organization.removeMember(user)).isTrue();
        assertThat(organization.members()).isEmpty();
    }

    @Test
    void theMemberSetCannotBeMutatedFromOutside() {
        Organization organization = active();

        assertThatThrownBy(() -> organization.members().add(UserId.generate(CLOCK)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "", "   "})
    void rejectsAnUnusableName(String name) {
        assertThatThrownBy(
                        () -> Organization.create(OrganizationId.generate(CLOCK), new OrganizationCode("VALID"), name))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANameLongerThanTheColumnAllows() {
        String tooLong = "x".repeat(121);

        assertThatThrownBy(() ->
                        Organization.create(OrganizationId.generate(CLOCK), new OrganizationCode("VALID"), tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsSurroundingWhitespaceFromTheName() {
        assertThat(Organization.create(OrganizationId.generate(CLOCK), new OrganizationCode("VALID"), "  Name  ")
                        .name())
                .isEqualTo("Name");
    }
}
