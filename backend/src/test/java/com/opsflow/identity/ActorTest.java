/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.platform.domain.Role;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActorTest {

    private static final UserId USER_ID = UserId.generate(Clock.systemUTC());

    @Test
    void anActorWithoutRolesHoldsNoRoleAtAll() {
        Actor actor = new Actor(USER_ID, Set.of());

        // The property that matters: an empty role set must never be read as
        // "unrestricted". Fail-open authorization is how a half-built principal
        // becomes an accidental administrator.
        assertThat(actor.isAdmin()).isFalse();
        for (Role role : Role.values()) {
            assertThat(actor.hasRole(role)).isFalse();
        }
    }

    @Test
    void aNullRoleSetIsTreatedAsNoRolesRatherThanFailing() {
        assertThat(new Actor(USER_ID, null).roles()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void reportsExactlyTheRolesItWasGiven(Role role) {
        Actor actor = new Actor(USER_ID, EnumSet.of(role));

        assertThat(actor.hasRole(role)).isTrue();
        assertThat(actor.roles()).containsExactly(role);
    }

    @Test
    void rolesAreUnmodifiable() {
        Actor actor = new Actor(USER_ID, EnumSet.of(Role.USER));

        assertThatThrownBy(() -> actor.roles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresAUserId() {
        assertThatThrownBy(() -> new Actor(null, Set.of(Role.USER))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carriesATypedIdentifierRatherThanARawUuid() {
        // The compiler is what stops a submission id or an organization id being passed
        // where a user id belongs; every one of them is a UUID underneath.
        assertThat(new Actor(USER_ID, Set.of()).userId()).isInstanceOf(UserId.class);
    }
}
