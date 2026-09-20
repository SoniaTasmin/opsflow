/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.opsflow.identity.domain.ExternalIdentity;
import com.opsflow.identity.domain.User;
import com.opsflow.identity.domain.UserRepository;
import com.opsflow.support.IntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Just-in-time provisioning against a real database.
 *
 * <p>The concurrency case here is the reason the unique constraint exists, and it cannot be
 * demonstrated with a mocked repository: the window between "no user found" and "insert" is
 * closed by PostgreSQL, not by application code.
 */
@IntegrationTest
class UserProvisioningServiceIntegrationTest {

    private static final String ISSUER = "https://issuer.example/realms/opsflow";

    @Autowired
    private UserProvisioningService provisioning;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private static ExternalIdentity identity(String subject) {
        return new ExternalIdentity(ISSUER, subject);
    }

    private static UserProfile profile(String username) {
        return new UserProfile(username, username + " Person", username + "@example.com");
    }

    @Test
    void provisionsALocalUserOnFirstAuthentication() {
        User user = provisioning.resolve(identity("first-login"), profile("first"));

        assertThat(user.id()).isNotNull();
        assertThat(users.findByExternalIdentity(identity("first-login"))).isPresent();
    }

    @Test
    void generatesAnApplicationOwnedIdentifierRatherThanReusingTheSubject() {
        // The subject is deliberately not a UUID here: if it were used as the primary
        // key this would not even be storable.
        User user = provisioning.resolve(identity("auth0|not-a-uuid-subject"), profile("external"));

        assertThat(user.id().value().version()).isEqualTo(7);
        assertThat(user.id().value().toString()).isNotEqualTo("auth0|not-a-uuid-subject");
    }

    @Test
    void returnsTheSameUserOnEverySubsequentRequest() {
        User first = provisioning.resolve(identity("returning"), profile("returning"));
        User second = provisioning.resolve(identity("returning"), profile("returning"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countUsersFor("returning")).isEqualTo(1);
    }

    @Test
    void adoptsAnUpstreamProfileChange() {
        provisioning.resolve(identity("renamer"), new UserProfile("renamer", "Original Name", "a@example.com"));

        User updated =
                provisioning.resolve(identity("renamer"), new UserProfile("renamer", "New Name", "b@example.com"));

        assertThat(updated.displayName()).isEqualTo("New Name");
        assertThat(updated.email()).contains("b@example.com");
    }

    @Test
    void storesNoRolesAgainstTheUser() {
        provisioning.resolve(identity("roleless"), profile("roleless"));

        // Guards ADR-0003 at the schema level: if a role column is ever added to
        // app_user, this fails and forces the conversation.
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'opsflow' AND table_name = 'app_user'",
                String.class);

        assertThat(columns)
                .noneSatisfy(column -> assertThat(column.toLowerCase()).contains("role"));
    }

    @Test
    void concurrentFirstLoginsCreateExactlyOneUser() throws Exception {
        int concurrentRequests = 8;
        ExternalIdentity identity = identity("simultaneous-first-login");
        CountDownLatch startTogether = new CountDownLatch(1);

        List<Callable<User>> attempts = IntStream.range(0, concurrentRequests)
                .<Callable<User>>mapToObj(i -> () -> {
                    startTogether.await(5, TimeUnit.SECONDS);
                    return provisioning.resolve(identity, profile("racer"));
                })
                .toList();

        try (ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests)) {
            List<Future<User>> futures = attempts.stream().map(pool::submit).toList();
            startTogether.countDown();

            Set<String> distinctIds = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(20, TimeUnit.SECONDS).id().toString();
                        } catch (Exception e) {
                            throw new AssertionError("A concurrent first login failed outright", e);
                        }
                    })
                    .collect(Collectors.toSet());

            // Every racer must end up with the same user. The loser of the insert race
            // adopts the winner's row rather than failing the request.
            assertThat(distinctIds).hasSize(1);
        }

        assertThat(countUsersFor("simultaneous-first-login")).isEqualTo(1);
    }

    private int countUsersFor(String subject) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM opsflow.app_user WHERE oidc_issuer = ? AND oidc_subject = ?",
                Integer.class,
                ISSUER,
                subject);
    }
}
