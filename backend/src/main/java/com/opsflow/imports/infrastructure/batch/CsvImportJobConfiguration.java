/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.application.ImportJobLifecycle;
import com.opsflow.imports.application.ImportJobParameters;
import com.opsflow.imports.application.ImportProperties;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.platform.correlation.MdcKeys;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.submission.SubmissionImportService;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.transaction.PlatformTransactionManager;

/** Step-scoped beans are rebuilt from the job's row: the parameters carry identifiers, never a principal. */
@Configuration
class CsvImportJobConfiguration {

    private static final String JOB_ID_PARAMETER = "#{jobParameters['" + ImportJobParameters.IMPORT_JOB_ID + "']}";
    private static final String CORRELATION_PARAMETER =
            "#{jobParameters['" + ImportJobParameters.CORRELATION_ID + "']}";

    @Bean
    Job csvImportJob(
            JobRepository jobRepository, Step validateHeaderStep, Step processRowsStep, ImportJobLifecycle lifecycle) {
        return new JobBuilder(ImportJobParameters.JOB_NAME, jobRepository)
                .start(validateHeaderStep)
                .next(processRowsStep)
                .listener(outcomeListener(lifecycle))
                .build();
    }

    @Bean
    Step validateHeaderStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            HeaderValidationTasklet headerValidationTasklet) {
        return new StepBuilder("validateHeaderStep", jobRepository)
                .tasklet(headerValidationTasklet, transactionManager)
                .build();
    }

    @Bean
    Step processRowsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<RawRow> importRowReader,
            RowValidationProcessor rowValidationProcessor,
            SubmissionApplyWriter submissionApplyWriter,
            ImportProperties properties) {
        return new StepBuilder("processRowsStep", jobRepository)
                .<RawRow, ParsedRow>chunk(properties.chunkSize(), transactionManager)
                .reader(importRowReader)
                .processor(rowValidationProcessor)
                .writer(submissionApplyWriter)
                .faultTolerant()
                // An import racing a human edit loses the version check and should reload.
                // Bounded: a conflict surviving three attempts is not transient, and a
                // validation failure must never be retried.
                .retry(ConflictException.class)
                .retryLimit(3)
                .build();
    }

    @Bean
    @StepScope
    HeaderValidationTasklet headerValidationTasklet(
            ImportFileStore files,
            ImportContextFactory contexts,
            @Value(JOB_ID_PARAMETER) String importJobId,
            @Value(CORRELATION_PARAMETER) String correlationId) {
        return new HeaderValidationTasklet(
                files, contexts.of(importJobId, correlationId).storageRef());
    }

    @Bean
    @StepScope
    FlatFileItemReader<RawRow> importRowReader(
            ImportFileStore files,
            ImportContextFactory contexts,
            @Value(JOB_ID_PARAMETER) String importJobId,
            @Value(CORRELATION_PARAMETER) String correlationId) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(CsvContract.COLUMNS.toArray(String[]::new));
        // A short line is a row problem, not a file problem: it is reported against its line.
        tokenizer.setStrict(false);

        LineMapper<RawRow> lineMapper = (line, lineNumber) -> {
            try {
                FieldSet fields = tokenizer.tokenize(line);
                return new RawRow(
                        lineNumber, field(fields, 0), field(fields, 1), field(fields, 2), field(fields, 3), null);
            } catch (RuntimeException malformed) {
                return RawRow.malformed(lineNumber, "The line could not be read as CSV");
            }
        };

        FlatFileItemReader<RawRow> reader = new FlatFileItemReader<>(lineMapper);
        reader.setName("importRowReader");
        reader.setResource(new InputStreamResource(
                files.open(contexts.of(importJobId, correlationId).storageRef())));
        reader.setLinesToSkip(1);
        return reader;
    }

    @Bean
    @StepScope
    RowValidationProcessor rowValidationProcessor(
            OrganizationDirectory organizations,
            PeriodQuery periods,
            ImportContextFactory contexts,
            @Value(JOB_ID_PARAMETER) String importJobId,
            @Value(CORRELATION_PARAMETER) String correlationId) {
        ImportContext context = contexts.of(importJobId, correlationId);
        return new RowValidationProcessor(
                organizations, periods, context.reportingPeriodId(), context.scopeOrganizationId());
    }

    @Bean
    @StepScope
    SubmissionApplyWriter submissionApplyWriter(
            SubmissionImportService submissions,
            ImportJobLifecycle lifecycle,
            ImportContextFactory contexts,
            @Value(JOB_ID_PARAMETER) String importJobId,
            @Value(CORRELATION_PARAMETER) String correlationId) {
        ImportContext context = contexts.of(importJobId, correlationId);
        return new SubmissionApplyWriter(
                submissions,
                lifecycle,
                context.jobId(),
                context.reportingPeriodId(),
                context.authority(),
                context.correlationId());
    }

    /** A failed step leaves the import FAILED with a readable reason, never COMPLETED. */
    private static JobExecutionListener outcomeListener(ImportJobLifecycle lifecycle) {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                MDC.put(MdcKeys.CORRELATION_ID, parameter(jobExecution, ImportJobParameters.CORRELATION_ID));
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                ImportJobId id =
                        ImportJobId.of(UUID.fromString(parameter(jobExecution, ImportJobParameters.IMPORT_JOB_ID)));
                try {
                    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                        lifecycle.markCompleted(id);
                    } else {
                        lifecycle.markFailed(id, failureOf(jobExecution));
                    }
                } finally {
                    MDC.remove(MdcKeys.CORRELATION_ID);
                }
            }
        };
    }

    private static ImportFailure failureOf(JobExecution jobExecution) {
        boolean headerMismatch =
                jobExecution.getAllFailureExceptions().stream().anyMatch(HeaderMismatchException.class::isInstance);
        return headerMismatch ? ImportFailure.HEADER_MISMATCH : ImportFailure.PROCESSING_FAILED;
    }

    private static String parameter(JobExecution jobExecution, String name) {
        return jobExecution.getJobParameters().getString(name);
    }

    private static String field(FieldSet fields, int index) {
        return index < fields.getFieldCount() ? fields.readString(index) : null;
    }
}
