/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.identity.application;

import com.opsflow.identity.UserId;
import com.opsflow.identity.domain.DuplicateExternalIdentityException;
import com.opsflow.identity.domain.ExternalIdentity;
import com.opsflow.identity.domain.User;
import com.opsflow.identity.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates and refreshes the local record for a person the identity provider has authenticated.
 *
 * <p>Provisioning is just-in-time: there is no user-creation API and no synchronisation job. A
 * person exists locally from the first request they make with a valid token, so the local table
 * can never drift ahead of the provider.
 *
 * <h2>Why each step runs in its own transaction</h2>
 *
 * Two simultaneous first-time logins both find no user and both insert; the unique constraint on
 * {@code (oidc_issuer, oidc_subject)} lets exactly one win. The loser must then read the row the
 * winner created — but in PostgreSQL a constraint violation <em>aborts the whole transaction</em>,
 * and every subsequent statement in it fails with "current transaction is aborted". Recovering
 * inside the failed transaction is therefore impossible.
 *
 * <p>So the insert attempt is isolated in its own transaction. When it fails, only that
 * transaction is poisoned, and the re-read runs in a clean one.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    /**
     * How stale {@code last_seen_at} may become before it is worth a write. Without this, every
     * request from every user issues an UPDATE for a field nothing depends on being precise.
     */
    private static final Duration LAST_SEEN_REFRESH_INTERVAL = Duration.ofMinutes(5);

    private final UserRepository users;
    private final Clock clock;
    private final TransactionTemplate isolatedTransaction;

    public UserProvisioningService(UserRepository users, Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.clock = clock;
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Resolves the external identity to a local user, creating one on first sight.
     *
     * @param profile display data from the current token; refreshed when it has changed upstream
     */
    public User resolve(ExternalIdentity externalIdentity, UserProfile profile) {
        Instant now = Instant.now(clock);

        Optional<User> existing = inIsolatedTransaction(() -> users.findByExternalIdentity(externalIdentity));
        if (existing.isPresent()) {
            return refresh(existing.get(), profile, now);
        }
        return provision(externalIdentity, profile, now);
    }

    private User refresh(User user, UserProfile profile, Instant now) {
        boolean changed = user.recordSeen(
                profile.username(), profile.displayName(), profile.email(), now, LAST_SEEN_REFRESH_INTERVAL);
        if (changed) {
            inIsolatedTransaction(() -> {
                users.update(user);
                return null;
            });
        }
        return user;
    }

    private User provision(ExternalIdentity externalIdentity, UserProfile profile, Instant now) {
        User candidate = User.provision(
                UserId.generate(clock),
                externalIdentity,
                profile.username(),
                profile.displayName(),
                profile.email(),
                now);
        try {
            User inserted = inIsolatedTransaction(() -> users.insert(candidate));
            log.info("Provisioned local user on first authentication: userId={}", inserted.id());
            return inserted;
        } catch (DuplicateExternalIdentityException ex) {
            // Another request won the insert race. Checking before inserting could not have
            // prevented this — only the constraint can — so this is expected control flow,
            // and the winner's row is the correct answer for both requests.
            log.debug("Concurrent first login detected; adopting the record created by the winning request");
            return inIsolatedTransaction(() -> users.findByExternalIdentity(externalIdentity))
                    .orElseThrow(() -> new IllegalStateException(
                            "Insert was rejected as a duplicate but no existing user was found", ex));
        }
    }

    private <T> T inIsolatedTransaction(Supplier<T> work) {
        return isolatedTransaction.execute(status -> work.get());
    }
}
