/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.platform.correlation.CorrelationId;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Component;

/**
 * The queue lives in this JVM, not on disk: a job enqueued but never started is recovered from
 * its PENDING row, which is the record of truth rather than this executor.
 */
@Component
public class ImportJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(ImportJobLauncher.class);

    private final JobOperator jobOperator;
    private final Job csvImportJob;
    private final ImportJobLifecycle lifecycle;
    private final ExecutorService executor;

    ImportJobLauncher(
            JobOperator jobOperator, Job csvImportJob, ImportJobLifecycle lifecycle, ImportProperties properties) {
        this.jobOperator = jobOperator;
        this.csvImportJob = csvImportJob;
        this.lifecycle = lifecycle;
        this.executor = Executors.newFixedThreadPool(properties.concurrency());
    }

    public void enqueue(ImportJobId id, CorrelationId correlationId) {
        executor.submit(() -> run(id, correlationId));
    }

    private void run(ImportJobId id, CorrelationId correlationId) {
        try {
            lifecycle.markRunning(id);
            JobParameters parameters = new JobParametersBuilder()
                    .addString(ImportJobParameters.IMPORT_JOB_ID, id.toString())
                    .addString(ImportJobParameters.CORRELATION_ID, correlationId.value())
                    .toJobParameters();
            jobOperator.start(csvImportJob, parameters);
        } catch (Exception failure) {
            log.error("Import job could not be run importJobId={}", id, failure);
            lifecycle.markFailed(id, ImportFailure.PROCESSING_FAILED);
        }
    }

    /** Lets the running chunk finish; anything still queued stays PENDING for recovery. */
    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
