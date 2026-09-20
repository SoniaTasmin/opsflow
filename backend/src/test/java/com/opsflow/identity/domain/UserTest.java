/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.identity.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Duration REFRESH_AFTER = Duration.ofMinutes(5);
    private static final Instant FIRST_SEEN = Instant.parse("2026-09-20T10:00:00Z");
    private static final ExternalIdentity IDENTITY = new ExternalIdentity("https://issuer", "subject-1");

    private User provisioned() {
        return User.provision(
                UserId.generate(Clock.fixed(FIRST_SEEN, ZoneOffset.UTC)),
                IDENTITY,
                "rreporter",
                "Ria Reporter",
                "ria@example.com",
                FIRST_SEEN);
    }

    @Test
    void aNewUserIsFirstAndLastSeenAtTheSameMoment() {
        User user = provisioned();

        assertThat(user.firstSeenAt()).isEqualTo(FIRST_SEEN);
        assertThat(user.lastSeenAt()).isEqualTo(FIRST_SEEN);
    }

    @Test
    void anUnchangedProfileSeenAgainImmediatelyNeedsNoWrite() {
        User user = provisioned();

        boolean changed = user.recordSeen(
                "rreporter", "Ria Reporter", "ria@example.com", FIRST_SEEN.plusSeconds(30), REFRESH_AFTER);

        // Every request would otherwise issue an UPDATE for a field nothing depends
        // on being precise.
        assertThat(changed).isFalse();
    }

    @Test
    void lastSeenIsRefreshedOnceItHasGoneStale() {
        User user = provisioned();

        boolean changed = user.recordSeen(
                "rreporter", "Ria Reporter", "ria@example.com", FIRST_SEEN.plusSeconds(600), REFRESH_AFTER);

        assertThat(changed).isTrue();
        assertThat(user.lastSeenAt()).isEqualTo(FIRST_SEEN.plusSeconds(600));
    }

    @Test
    void anUpstreamProfileChangeIsAdoptedImmediately() {
        User user = provisioned();

        boolean changed = user.recordSeen(
                "rreporter", "Ria Reporter-Smith", "ria@example.com", FIRST_SEEN.plusSeconds(5), REFRESH_AFTER);

        assertThat(changed).isTrue();
        assertThat(user.displayName()).isEqualTo("Ria Reporter-Smith");
    }

    @Test
    void identityNeverChangesWhenTheProfileDoes() {
        User user = provisioned();
        UserId originalId = user.id();

        user.recordSeen("renamed", "Renamed Person", null, FIRST_SEEN.plusSeconds(600), REFRESH_AFTER);

        assertThat(user.id()).isEqualTo(originalId);
        assertThat(user.externalIdentity()).isEqualTo(IDENTITY);
        assertThat(user.email()).isEmpty();
    }
}
