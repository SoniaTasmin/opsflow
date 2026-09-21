/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.imports.domain.ImportFileStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** Reads the first line only: a wrong header means no row should be touched at all. */
class HeaderValidationTasklet implements Tasklet {

    private final ImportFileStore files;
    private final String storageRef;

    HeaderValidationTasklet(ImportFileStore files, String storageRef) {
        this.files = files;
        this.storageRef = storageRef;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(files.open(storageRef), StandardCharsets.UTF_8))) {
            if (!CsvContract.matches(reader.readLine())) {
                throw new HeaderMismatchException();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return RepeatStatus.FINISHED;
    }
}
