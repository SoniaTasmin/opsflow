/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.ImportAuthorizationMode;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ImportJobTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final Instant NOW = Instant.parse("2026-04-01T09:00:00Z");

    private static ImportJob pending() {
        return ImportJob.builder()
                .id(ImportJobId.generate(CLOCK))
                .reportingPeriodId(PeriodId.generate(CLOCK))
                .organizationId(OrganizationId.generate(CLOCK))
                .initiatedBy(UserId.generate(CLOCK))
                .authorizationMode(ImportAuthorizationMode.ORGANIZATION_MEMBER)
                .idempotencyKey("key-1")
                .requestFingerprint("fingerprint")
                .originalFilename("values.csv")
                .storageRef("ref.csv")
                .fileSizeBytes(120)
                .fileChecksum("a".repeat(64))
                .submittedRows(3)
                .build();
    }

    @Test
    void aNewJobIsPendingAndHasDoneNothing() {
        ImportJob job = pending();

        assertThat(job.status()).isEqualTo(ImportStatus.PENDING);
        assertThat(job.processedRows()).isZero();
        assertThat(job.startedAt()).isEmpty();
    }

    @Test
    void progressAccumulatesAcrossChunks() {
        ImportJob job = pending();
        job.start(NOW);

        job.recordProgress(500, 480, 20);
        job.recordProgress(200, 200, 0);

        assertThat(job.processedRows()).isEqualTo(700);
        assertThat(job.validRows()).isEqualTo(680);
        assertThat(job.invalidRows()).isEqualTo(20);
    }

    @Test
    void completingRecordsWhenItFinished() {
        ImportJob job = pending();
        job.start(NOW);

        job.complete(NOW.plusSeconds(60));

        assertThat(job.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(job.finishedAt()).contains(NOW.plusSeconds(60));
        assertThat(job.failure()).isEmpty();
    }

    @Test
    void failingKeepsTheReasonTheUserWillSee() {
        ImportJob job = pending();
        job.start(NOW);

        job.fail(ImportFailure.INTERRUPTED_BY_SHUTDOWN, NOW);

        assertThat(job.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.failure()).contains(ImportFailure.INTERRUPTED_BY_SHUTDOWN);
    }

    @Test
    void aJobThatNeverStartedCanStillBeFailed() {
        ImportJob job = pending();

        job.fail(ImportFailure.PROCESSING_FAILED, NOW);

        assertThat(job.status()).isEqualTo(ImportStatus.FAILED);
    }

    @Test
    void refusesToStartTwice() {
        ImportJob job = pending();
        job.start(NOW);

        assertThatThrownBy(() -> job.start(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesToCompleteSomethingThatNeverRan() {
        assertThatThrownBy(() -> pending().complete(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesToReopenAFinishedJob() {
        ImportJob job = pending();
        job.start(NOW);
        job.complete(NOW);

        assertThatThrownBy(() -> job.fail(ImportFailure.PROCESSING_FAILED, NOW)).isInstanceOf(ConflictException.class);
    }
}
