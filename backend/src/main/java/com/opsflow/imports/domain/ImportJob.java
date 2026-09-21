/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.ImportAuthorizationMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ImportJob {

    private final ImportJobId id;
    private final PeriodId reportingPeriodId;
    private final OrganizationId organizationId;
    private final UserId initiatedBy;
    private final ImportAuthorizationMode authorizationMode;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final String originalFilename;
    private final String storageRef;
    private final long fileSizeBytes;
    private final String fileChecksum;
    private final int submittedRows;

    private ImportStatus status;
    private int processedRows;
    private int validRows;
    private int invalidRows;
    private ImportFailure failure;
    private Instant startedAt;
    private Instant finishedAt;
    private final long version;

    private ImportJob(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is required");
        this.reportingPeriodId = Objects.requireNonNull(builder.reportingPeriodId, "reportingPeriodId is required");
        this.organizationId = builder.organizationId;
        this.initiatedBy = Objects.requireNonNull(builder.initiatedBy, "initiatedBy is required");
        this.authorizationMode = Objects.requireNonNull(builder.authorizationMode, "authorizationMode is required");
        this.idempotencyKey = Objects.requireNonNull(builder.idempotencyKey, "idempotencyKey is required");
        this.requestFingerprint = Objects.requireNonNull(builder.requestFingerprint, "requestFingerprint is required");
        this.originalFilename = Objects.requireNonNull(builder.originalFilename, "originalFilename is required");
        this.storageRef = Objects.requireNonNull(builder.storageRef, "storageRef is required");
        this.fileSizeBytes = builder.fileSizeBytes;
        this.fileChecksum = Objects.requireNonNull(builder.fileChecksum, "fileChecksum is required");
        this.submittedRows = builder.submittedRows;
        this.status = Objects.requireNonNull(builder.status, "status is required");
        this.processedRows = builder.processedRows;
        this.validRows = builder.validRows;
        this.invalidRows = builder.invalidRows;
        this.failure = builder.failure;
        this.startedAt = builder.startedAt;
        this.finishedAt = builder.finishedAt;
        this.version = builder.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start(Instant now) {
        requireStatus("start", ImportStatus.PENDING);
        status = ImportStatus.RUNNING;
        startedAt = now;
    }

    public void complete(Instant now) {
        requireStatus("complete", ImportStatus.RUNNING);
        status = ImportStatus.COMPLETED;
        finishedAt = now;
    }

    public void fail(ImportFailure reason, Instant now) {
        requireStatus("fail", ImportStatus.PENDING, ImportStatus.RUNNING);
        status = ImportStatus.FAILED;
        failure = Objects.requireNonNull(reason, "reason is required");
        finishedAt = now;
    }

    public void recordProgress(int processed, int valid, int invalid) {
        processedRows += processed;
        validRows += valid;
        invalidRows += invalid;
    }

    private void requireStatus(String command, ImportStatus... allowed) {
        for (ImportStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new ConflictException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "An import cannot be " + command + "ed from " + status,
                Map.of("importJobId", id.toString(), "status", status.name()));
    }

    public ImportJobId id() {
        return id;
    }

    public PeriodId reportingPeriodId() {
        return reportingPeriodId;
    }

    public Optional<OrganizationId> organizationId() {
        return Optional.ofNullable(organizationId);
    }

    public UserId initiatedBy() {
        return initiatedBy;
    }

    public ImportAuthorizationMode authorizationMode() {
        return authorizationMode;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String storageRef() {
        return storageRef;
    }

    public long fileSizeBytes() {
        return fileSizeBytes;
    }

    public String fileChecksum() {
        return fileChecksum;
    }

    public int submittedRows() {
        return submittedRows;
    }

    public ImportStatus status() {
        return status;
    }

    public int processedRows() {
        return processedRows;
    }

    public int validRows() {
        return validRows;
    }

    public int invalidRows() {
        return invalidRows;
    }

    public Optional<ImportFailure> failure() {
        return Optional.ofNullable(failure);
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public long version() {
        return version;
    }

    public static final class Builder {
        private ImportJobId id;
        private PeriodId reportingPeriodId;
        private OrganizationId organizationId;
        private UserId initiatedBy;
        private ImportAuthorizationMode authorizationMode;
        private String idempotencyKey;
        private String requestFingerprint;
        private String originalFilename;
        private String storageRef;
        private long fileSizeBytes;
        private String fileChecksum;
        private int submittedRows;
        private ImportStatus status = ImportStatus.PENDING;
        private int processedRows;
        private int validRows;
        private int invalidRows;
        private ImportFailure failure;
        private Instant startedAt;
        private Instant finishedAt;
        private long version;

        public Builder id(ImportJobId value) {
            this.id = value;
            return this;
        }

        public Builder reportingPeriodId(PeriodId value) {
            this.reportingPeriodId = value;
            return this;
        }

        public Builder organizationId(OrganizationId value) {
            this.organizationId = value;
            return this;
        }

        public Builder initiatedBy(UserId value) {
            this.initiatedBy = value;
            return this;
        }

        public Builder authorizationMode(ImportAuthorizationMode value) {
            this.authorizationMode = value;
            return this;
        }

        public Builder idempotencyKey(String value) {
            this.idempotencyKey = value;
            return this;
        }

        public Builder requestFingerprint(String value) {
            this.requestFingerprint = value;
            return this;
        }

        public Builder originalFilename(String value) {
            this.originalFilename = value;
            return this;
        }

        public Builder storageRef(String value) {
            this.storageRef = value;
            return this;
        }

        public Builder fileSizeBytes(long value) {
            this.fileSizeBytes = value;
            return this;
        }

        public Builder fileChecksum(String value) {
            this.fileChecksum = value;
            return this;
        }

        public Builder submittedRows(int value) {
            this.submittedRows = value;
            return this;
        }

        public Builder status(ImportStatus value) {
            this.status = value;
            return this;
        }

        public Builder counters(int processed, int valid, int invalid) {
            this.processedRows = processed;
            this.validRows = valid;
            this.invalidRows = invalid;
            return this;
        }

        public Builder failure(ImportFailure value) {
            this.failure = value;
            return this;
        }

        public Builder startedAt(Instant value) {
            this.startedAt = value;
            return this;
        }

        public Builder finishedAt(Instant value) {
            this.finishedAt = value;
            return this;
        }

        public Builder version(long value) {
            this.version = value;
            return this;
        }

        public ImportJob build() {
            return new ImportJob(this);
        }
    }
}
