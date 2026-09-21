/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.persistence;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.imports.domain.DuplicateIdempotencyKeyException;
import com.opsflow.imports.domain.ImportFailure;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.imports.domain.ImportJobView;
import com.opsflow.imports.domain.ImportRowError;
import com.opsflow.imports.domain.ImportSearchCriteria;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.domain.UuidV7;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.ImportAuthorizationMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcImportJobRepository implements ImportJobRepository {

    private static final String SELECT =
            """
            SELECT id, reporting_period_id, organization_id, created_by, status, authorization_mode,
                   idempotency_key, request_fingerprint, original_filename, storage_ref,
                   file_size_bytes, file_checksum, submitted_rows, processed_rows, valid_rows,
                   invalid_rows, failure_code, started_at, finished_at, created_at, version
              FROM opsflow.import_job
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcImportJobRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<ImportJob> findById(ImportJobId id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id.value())
                .query(JdbcImportJobRepository::toJob)
                .optional();
    }

    @Override
    public Optional<ImportJobView> viewById(ImportJobId id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id.value())
                .query(JdbcImportJobRepository::toView)
                .optional();
    }

    @Override
    public Optional<ImportJob> findByIdempotencyKey(UserId initiatedBy, String idempotencyKey) {
        return jdbc.sql(SELECT + " WHERE created_by = :createdBy AND idempotency_key = :key")
                .param("createdBy", initiatedBy.value())
                .param("key", idempotencyKey)
                .query(JdbcImportJobRepository::toJob)
                .optional();
    }

    @Override
    public ImportJob insert(ImportJob job) {
        try {
            jdbc.sql(
                            """
                    INSERT INTO opsflow.import_job
                           (id, reporting_period_id, organization_id, created_by, status,
                            authorization_mode, idempotency_key, request_fingerprint,
                            original_filename, storage_ref, file_size_bytes, file_checksum,
                            submitted_rows, updated_at)
                    VALUES (:id, :periodId, :organizationId, :createdBy, :status,
                            :mode, :key, :fingerprint,
                            :filename, :storageRef, :size, :checksum,
                            :submittedRows, :now)
                    """)
                    .param("id", job.id().value())
                    .param("periodId", job.reportingPeriodId().value())
                    .param(
                            "organizationId",
                            job.organizationId().map(OrganizationId::value).orElse(null))
                    .param("createdBy", job.initiatedBy().value())
                    .param("status", job.status().name())
                    .param("mode", job.authorizationMode().name())
                    .param("key", job.idempotencyKey())
                    .param("fingerprint", job.requestFingerprint())
                    .param("filename", job.originalFilename())
                    .param("storageRef", job.storageRef())
                    .param("size", job.fileSizeBytes())
                    .param("checksum", job.fileChecksum())
                    .param("submittedRows", job.submittedRows())
                    .param("now", now())
                    .update();
        } catch (DuplicateKeyException ex) {
            // Which index was hit decides the answer: a repeated key resolves to the job that
            // won the race, while a second live import for one scope is a genuine conflict.
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("ux_import_idempotency")) {
                throw new DuplicateIdempotencyKeyException(job.idempotencyKey());
            }
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "An import is already running for this period and scope",
                    Map.of("reportingPeriodId", job.reportingPeriodId().toString()));
        }
        return findById(job.id()).orElseThrow();
    }

    @Override
    public ImportJob save(ImportJob job) {
        int updated = jdbc.sql(
                        """
                UPDATE opsflow.import_job
                   SET status = :status,
                       processed_rows = :processed,
                       valid_rows = :valid,
                       invalid_rows = :invalid,
                       failure_code = :failureCode,
                       failure_message = :failureMessage,
                       started_at = :startedAt,
                       finished_at = :finishedAt,
                       updated_at = :now,
                       version = version + 1
                 WHERE id = :id AND version = :expectedVersion
                """)
                .param("status", job.status().name())
                .param("processed", job.processedRows())
                .param("valid", job.validRows())
                .param("invalid", job.invalidRows())
                .param("failureCode", job.failure().map(Enum::name).orElse(null))
                .param(
                        "failureMessage",
                        job.failure().map(ImportFailure::message).orElse(null))
                .param("startedAt", job.startedAt().map(this::at).orElse(null))
                .param("finishedAt", job.finishedAt().map(this::at).orElse(null))
                .param("now", now())
                .param("id", job.id().value())
                .param("expectedVersion", job.version())
                .update();

        if (updated == 0) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    "The import job was modified concurrently",
                    Map.of("importJobId", job.id().toString(), "expectedVersion", job.version()));
        }
        return findById(job.id()).orElseThrow();
    }

    /**
     * Counters move forward by whole chunks, which is why they are added rather than assigned:
     * a chunk that retries must not be able to double-count or reset what earlier chunks wrote.
     */
    @Override
    public void recordProgress(ImportJobId id, int processed, int valid, int invalid) {
        jdbc.sql(
                        """
                UPDATE opsflow.import_job
                   SET processed_rows = processed_rows + :processed,
                       valid_rows = valid_rows + :valid,
                       invalid_rows = invalid_rows + :invalid,
                       updated_at = :now
                 WHERE id = :id
                """)
                .param("processed", processed)
                .param("valid", valid)
                .param("invalid", invalid)
                .param("now", now())
                .param("id", id.value())
                .update();
    }

    @Override
    public ImportPage search(ImportSearchCriteria criteria) {
        List<UUID> visible = criteria.visibleOrganizations() == null
                ? null
                : criteria.visibleOrganizations().stream()
                        .map(OrganizationId::value)
                        .toList();

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (criteria.onlyInitiatedBy() != null) {
            // A caller who may see only their own work: scoped imports they may see, plus
            // anything they started themselves.
            where.append(" AND (created_by = :initiatedBy");
            if (visible != null && !visible.isEmpty()) {
                where.append(" OR organization_id = ANY (:visible)");
            }
            where.append(")");
        }
        if (criteria.reportingPeriodId() != null) {
            where.append(" AND reporting_period_id = :periodId");
        }
        if (criteria.status() != null) {
            where.append(" AND status = :status");
        }

        var content = jdbc.sql(SELECT + where + " ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset");
        var count = jdbc.sql("SELECT count(*) FROM opsflow.import_job" + where);
        for (var spec : List.of(content, count)) {
            if (criteria.onlyInitiatedBy() != null) {
                spec.param("initiatedBy", criteria.onlyInitiatedBy().value());
                if (visible != null && !visible.isEmpty()) {
                    spec.param("visible", visible.toArray(UUID[]::new));
                }
            }
            if (criteria.reportingPeriodId() != null) {
                spec.param("periodId", criteria.reportingPeriodId().value());
            }
            if (criteria.status() != null) {
                spec.param("status", criteria.status().name());
            }
        }

        List<ImportJobView> rows = content.param("limit", criteria.size())
                .param("offset", (long) criteria.page() * criteria.size())
                .query(JdbcImportJobRepository::toView)
                .list();
        return new ImportPage(rows, count.query(Long.class).single());
    }

    @Override
    public List<ImportJob> findByStatus(ImportStatus status) {
        return jdbc.sql(SELECT + " WHERE status = :status ORDER BY created_at")
                .param("status", status.name())
                .query(JdbcImportJobRepository::toJob)
                .list();
    }

    @Override
    public void appendErrors(ImportJobId id, List<ImportRowError> errors) {
        Instant now = Instant.now(clock);
        errors.forEach(error -> jdbc.sql(
                        """
                INSERT INTO opsflow.import_row_error
                       (id, import_job_id, row_number, code, message, affected_rows, created_at)
                VALUES (:id, :jobId, :rowNumber, :code, :message, :affectedRows, :now)
                """)
                .param("id", UuidV7.generate(now))
                .param("jobId", id.value())
                .param("rowNumber", error.rowNumber())
                .param("code", error.code())
                .param("message", truncate(error.message()))
                .param("affectedRows", error.affectedRows())
                .param("now", at(now))
                .update());
    }

    @Override
    public List<ImportRowError> errorsOf(ImportJobId id, int limit) {
        return jdbc.sql(
                        """
                SELECT row_number, code, message, affected_rows
                  FROM opsflow.import_row_error
                 WHERE import_job_id = :id
                 ORDER BY row_number, id
                 LIMIT :limit
                """)
                .param("id", id.value())
                .param("limit", limit)
                .query((rs, rowNum) -> new ImportRowError(
                        rs.getInt("row_number"),
                        rs.getString("code"),
                        rs.getString("message"),
                        rs.getInt("affected_rows")))
                .list();
    }

    @Override
    public int errorCount(ImportJobId id) {
        return jdbc.sql("SELECT count(*) FROM opsflow.import_row_error WHERE import_job_id = :id")
                .param("id", id.value())
                .query(Integer.class)
                .single();
    }

    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private OffsetDateTime now() {
        return at(Instant.now(clock));
    }

    private OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, clock.getZone());
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static ImportJob toJob(ResultSet rs, int rowNum) throws SQLException {
        String failure = rs.getString("failure_code");
        UUID organizationId = rs.getObject("organization_id", UUID.class);
        return ImportJob.builder()
                .id(ImportJobId.of(rs.getObject("id", UUID.class)))
                .reportingPeriodId(PeriodId.of(rs.getObject("reporting_period_id", UUID.class)))
                .organizationId(organizationId == null ? null : OrganizationId.of(organizationId))
                .initiatedBy(UserId.of(rs.getObject("created_by", UUID.class)))
                .status(ImportStatus.valueOf(rs.getString("status")))
                .authorizationMode(ImportAuthorizationMode.valueOf(rs.getString("authorization_mode")))
                .idempotencyKey(rs.getString("idempotency_key"))
                .requestFingerprint(rs.getString("request_fingerprint"))
                .originalFilename(rs.getString("original_filename"))
                .storageRef(rs.getString("storage_ref"))
                .fileSizeBytes(rs.getLong("file_size_bytes"))
                .fileChecksum(rs.getString("file_checksum"))
                .submittedRows(rs.getInt("submitted_rows"))
                .counters(rs.getInt("processed_rows"), rs.getInt("valid_rows"), rs.getInt("invalid_rows"))
                .failure(failure == null ? null : ImportFailure.valueOf(failure))
                .startedAt(toInstant(rs.getObject("started_at", OffsetDateTime.class)))
                .finishedAt(toInstant(rs.getObject("finished_at", OffsetDateTime.class)))
                .version(rs.getLong("version"))
                .build();
    }

    private static ImportJobView toView(ResultSet rs, int rowNum) throws SQLException {
        String failure = rs.getString("failure_code");
        UUID organizationId = rs.getObject("organization_id", UUID.class);
        return new ImportJobView(
                ImportJobId.of(rs.getObject("id", UUID.class)),
                PeriodId.of(rs.getObject("reporting_period_id", UUID.class)),
                organizationId == null ? null : OrganizationId.of(organizationId),
                UserId.of(rs.getObject("created_by", UUID.class)),
                ImportStatus.valueOf(rs.getString("status")),
                rs.getString("original_filename"),
                rs.getLong("file_size_bytes"),
                rs.getInt("submitted_rows"),
                rs.getInt("processed_rows"),
                rs.getInt("valid_rows"),
                rs.getInt("invalid_rows"),
                failure == null ? null : ImportFailure.valueOf(failure),
                toInstant(rs.getObject("started_at", OffsetDateTime.class)),
                toInstant(rs.getObject("finished_at", OffsetDateTime.class)),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                rs.getLong("version"));
    }
}
