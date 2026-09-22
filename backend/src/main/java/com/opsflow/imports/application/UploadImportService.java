/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.identity.Actor;
import com.opsflow.identity.CurrentActorProvider;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.DuplicateIdempotencyKeyException;
import com.opsflow.imports.domain.ImportFileStore;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.imports.domain.StoredFile;
import com.opsflow.organization.OrganizationAccess;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.platform.correlation.CorrelationId;
import com.opsflow.platform.error.AccessDeniedDomainException;
import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.platform.error.MalformedRequestException;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.PeriodSummary;
import com.opsflow.submission.ImportAuthorizationMode;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Admission is the only place a real token is available, so it is the only place authority is
 * decided. It records the basis for that decision, never the roles.
 */
@Service
@EnableConfigurationProperties(ImportProperties.class)
public class UploadImportService {

    private static final String CSV_EXTENSION = ".csv";

    private final ImportJobRepository jobs;
    private final ImportFileStore files;
    private final CurrentActorProvider currentActor;
    private final OrganizationAccess access;
    private final OrganizationDirectory organizations;
    private final PeriodQuery periods;
    private final ImportProperties properties;
    private final ImportJobLauncher launcher;
    private final Clock clock;

    UploadImportService(
            ImportJobRepository jobs,
            ImportFileStore files,
            CurrentActorProvider currentActor,
            OrganizationAccess access,
            OrganizationDirectory organizations,
            PeriodQuery periods,
            ImportProperties properties,
            ImportJobLauncher launcher,
            Clock clock) {
        this.jobs = jobs;
        this.files = files;
        this.currentActor = currentActor;
        this.access = access;
        this.organizations = organizations;
        this.periods = periods;
        this.properties = properties;
        this.launcher = launcher;
        this.clock = clock;
    }

    public UploadOutcome upload(UploadImportCommand command) {
        Actor actor = currentActor.requireActor();
        requireIdempotencyKey(command.idempotencyKey());
        requireCsv(command);

        ImportAuthorizationMode mode = admit(actor, command.organizationId());
        PeriodSummary period = requireOpenPeriod(command);
        requireEligibleScope(command.organizationId(), period);

        ImportJobId jobId = ImportJobId.generate(clock);
        StoredFile stored = files.store(jobId, command.content());
        try {
            requireWithinRowLimit(stored);
            String fingerprint =
                    RequestFingerprint.of(stored.checksum(), command.reportingPeriodId(), command.organizationId());

            Optional<ImportJob> existing = jobs.findByIdempotencyKey(actor.userId(), command.idempotencyKey());
            if (existing.isPresent()) {
                return replay(existing.get(), fingerprint, stored);
            }

            ImportJob created;
            try {
                created = create(command, actor, mode, jobId, stored, fingerprint);
            } catch (DuplicateIdempotencyKeyException raced) {
                // Two requests with one key arrived together; the unique index picked a winner.
                return replay(
                        jobs.findByIdempotencyKey(actor.userId(), command.idempotencyKey())
                                .orElseThrow(),
                        fingerprint,
                        stored);
            }
            // Enqueued only after the row exists, so the worker cannot look for a job that has
            // not been written yet.
            launcher.enqueue(
                    created.id(),
                    CorrelationId.acceptOrGenerate(CorrelationId.current().orElse(null)));
            return new UploadOutcome(jobs.viewById(created.id()).orElseThrow(), true);
        } catch (RuntimeException failure) {
            files.delete(stored.ref());
            throw failure;
        }
    }

    private ImportJob create(
            UploadImportCommand command,
            Actor actor,
            ImportAuthorizationMode mode,
            ImportJobId jobId,
            StoredFile stored,
            String fingerprint) {
        ImportJob job = jobs.insert(ImportJob.builder()
                .id(jobId)
                .reportingPeriodId(command.reportingPeriodId())
                .organizationId(command.organizationId())
                .initiatedBy(actor.userId())
                .authorizationMode(mode)
                .idempotencyKey(command.idempotencyKey())
                .requestFingerprint(fingerprint)
                .originalFilename(sanitize(command.originalFilename()))
                .storageRef(stored.ref())
                .fileSizeBytes(stored.sizeBytes())
                .fileChecksum(stored.checksum())
                .submittedRows(stored.lineCount())
                .build());
        return job;
    }

    /**
     * Any difference is a conflict: returning the first file's job would leave the caller
     * believing the second had been applied.
     */
    private UploadOutcome replay(ImportJob existing, String fingerprint, StoredFile stored) {
        files.delete(stored.ref());
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "This idempotency key was already used for a different import; use a new key",
                    Map.of("importJobId", existing.id().toString()));
        }
        return new UploadOutcome(jobs.viewById(existing.id()).orElseThrow(), false);
    }

    /** Roles decide the basis; the request never gets to ask for one. */
    private ImportAuthorizationMode admit(Actor actor, OrganizationId organizationId) {
        if (actor.isAdmin()) {
            return ImportAuthorizationMode.ADMIN_OVERRIDE;
        }
        if (organizationId != null && !access.isMember(actor.userId(), organizationId)) {
            throw new AccessDeniedDomainException("You do not belong to this organization");
        }
        if (organizationId == null && access.organizationIdsOf(actor.userId()).isEmpty()) {
            throw new AccessDeniedDomainException("You do not belong to any organization");
        }
        return ImportAuthorizationMode.ORGANIZATION_MEMBER;
    }

    private PeriodSummary requireOpenPeriod(UploadImportCommand command) {
        PeriodSummary period = periods.findById(command.reportingPeriodId())
                .orElseThrow(() -> NotFoundException.of("Period", command.reportingPeriodId()));
        if (!period.isOpen()) {
            throw new BusinessRuleException(
                    "PERIOD_NOT_OPEN",
                    "This reporting period is not open for submissions",
                    Map.of("reportingPeriodId", period.id().toString()));
        }
        return period;
    }

    private void requireEligibleScope(OrganizationId organizationId, PeriodSummary period) {
        if (organizationId == null) {
            return;
        }
        OrganizationSummary organization = organizations
                .findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("Organization", organizationId));
        if (!organization.active()) {
            throw new BusinessRuleException(
                    "ORGANIZATION_NOT_ACTIVE",
                    "An archived organization cannot report",
                    Map.of("organizationId", organizationId.toString()));
        }
        if (!periods.isParticipant(period.id(), organizationId)) {
            throw new BusinessRuleException(
                    "ORGANIZATION_NOT_IN_PERIOD_SCOPE",
                    "This organization does not report in this period",
                    Map.of("organizationId", organizationId.toString()));
        }
    }

    private void requireWithinRowLimit(StoredFile stored) {
        if (stored.lineCount() > properties.maxRows()) {
            throw new BusinessRuleException(
                    "ROW_LIMIT_EXCEEDED",
                    "An import may carry at most " + properties.maxRows() + " rows",
                    Map.of("rows", stored.lineCount(), "limit", properties.maxRows()));
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new MalformedRequestException(
                    "IDEMPOTENCY_KEY_REQUIRED", "An Idempotency-Key header is required for an import");
        }
    }

    private static void requireCsv(UploadImportCommand command) {
        String filename = command.originalFilename() == null
                ? ""
                : command.originalFilename().toLowerCase(Locale.ROOT);
        String contentType =
                command.contentType() == null ? "" : command.contentType().toLowerCase(Locale.ROOT);
        boolean acceptable = filename.endsWith(CSV_EXTENSION)
                || contentType.startsWith("text/csv")
                || contentType.startsWith("application/csv");
        if (!acceptable) {
            throw new BusinessRuleException(
                    "UNSUPPORTED_IMPORT_FORMAT", "Imports accept CSV files only", Map.of("contentType", contentType));
        }
    }

    /** A label, never a path element; control characters would corrupt a log line. */
    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.csv";
        }
        String cleaned = filename.replaceAll("[\\p{Cntrl}/\\\\]", "").trim();
        if (cleaned.isEmpty()) {
            return "upload.csv";
        }
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(0, 255);
    }
}
