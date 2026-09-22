/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.platform.correlation.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An interrupted import is not resumed: resuming from a half-applied chunk means guessing what
 * the dead process committed. It is failed with its last counts and the user re-uploads, which is
 * safe because applying the same values again converges (ADR-0009).
 */
@Component
class ImportJobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ImportJobRecoveryService.class);

    // Recovery assumes a single replica (ADR-0009). The lock turns that assumption into
    // something enforced: a second instance finds it taken and does nothing.
    private static final long RECOVERY_LOCK = 8_170_242_001L;

    private final ImportJobRepository jobs;
    private final ImportJobLauncher launcher;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;
    private final Clock clock;

    ImportJobRecoveryService(
            ImportJobRepository jobs,
            ImportJobLauncher launcher,
            PlatformTransactionManager transactionManager,
            JdbcClient jdbc,
            Clock clock) {
        this.jobs = jobs;
        this.launcher = launcher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recover() {
        List<ImportJobId> toEnqueue = transactions.execute(status -> {
            if (!Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                    .param("key", RECOVERY_LOCK)
                    .query(Boolean.class)
                    .single())) {
                log.info("Import recovery skipped: another instance holds the recovery lock");
                return List.of();
            }

            jobs.findByStatus(ImportStatus.RUNNING).forEach(job -> {
                log.warn(
                        "Failing an import left running by a previous process importJobId={} processedRows={}",
                        job.id(),
                        job.processedRows());
                job.fail(ImportFailure.INTERRUPTED_BY_SHUTDOWN, Instant.now(clock));
                jobs.save(job);
            });

            List<ImportJobId> pending = new ArrayList<>();
            jobs.findByStatus(ImportStatus.PENDING).stream().map(ImportJob::id).forEach(pending::add);
            return pending;
        });

        if (toEnqueue == null || toEnqueue.isEmpty()) {
            return;
        }
        log.info("Re-enqueueing imports that had not started count={}", toEnqueue.size());
        toEnqueue.forEach(id -> launcher.enqueue(id, CorrelationId.generate()));
    }
}
