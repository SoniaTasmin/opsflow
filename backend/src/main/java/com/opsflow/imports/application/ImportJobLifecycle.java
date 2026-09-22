/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.imports.domain.ImportRowError;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportJobLifecycle {

    private final ImportJobRepository jobs;
    private final ImportProperties properties;
    private final Clock clock;

    ImportJobLifecycle(ImportJobRepository jobs, ImportProperties properties, Clock clock) {
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void markRunning(ImportJobId id) {
        ImportJob job = jobs.findById(id).orElseThrow();
        job.start(Instant.now(clock));
        jobs.save(job);
    }

    @Transactional
    public void markCompleted(ImportJobId id) {
        ImportJob job = jobs.findById(id).orElseThrow();
        job.complete(Instant.now(clock));
        jobs.save(job);
    }

    @Transactional
    public void markFailed(ImportJobId id, ImportFailure reason) {
        ImportJob job = jobs.findById(id).orElseThrow();
        if (job.status().terminal()) {
            return;
        }
        job.fail(reason, Instant.now(clock));
        jobs.save(job);
    }

    /** In the chunk's transaction: a chunk that fails must not leave rows counted as applied. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChunk(ImportJobId id, int processed, int valid, int invalid, List<ImportRowError> errors) {
        jobs.recordProgress(id, processed, valid, invalid);
        if (errors.isEmpty()) {
            return;
        }
        int room = properties.maxErrors() - jobs.errorCount(id);
        if (room > 0) {
            jobs.appendErrors(id, errors.size() <= room ? errors : errors.subList(0, room));
        }
    }
}
