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
 * Just-in-time provisioning: a person exists locally from their first authenticated request.
 *
 * <p>Each step runs in its own transaction because PostgreSQL aborts the whole transaction on a
 * constraint violation. Two simultaneous first logins both insert, one loses the unique index,
 * and the loser can only re-read the winner's row from a clean transaction.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    // Without a threshold every request would UPDATE a field nothing needs to be precise.
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

    /** @param profile display data from the current token, refreshed when it changed upstream */
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
            // Expected control flow: only the constraint can decide this race, and the winner's
            // row is the right answer for both requests.
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
