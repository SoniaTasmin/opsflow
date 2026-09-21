/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.api;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.imports.api.dto.ImportJobResponse;
import com.opsflow.imports.api.dto.ImportRowErrorResponse;
import com.opsflow.imports.application.ImportQueryService;
import com.opsflow.imports.application.UploadImportCommand;
import com.opsflow.imports.application.UploadImportService;
import com.opsflow.imports.application.UploadOutcome;
import com.opsflow.imports.domain.ImportJobRepository;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.web.PageResponse;
import com.opsflow.reporting.PeriodId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/v1/imports", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Imports", description = "CSV bulk import of submission values")
public class ImportController {

    private final UploadImportService uploads;
    private final ImportQueryService queries;

    public ImportController(UploadImportService uploads, ImportQueryService queries) {
        this.uploads = uploads;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
            operationId = "startImport",
            summary = "Upload a CSV and start an import",
            description = "Idempotency-Key is required. The same key with the same file, period and scope"
                    + " returns the existing job; any difference is a conflict.")
    public ResponseEntity<ImportJobResponse> start(
            @RequestPart("file") MultipartFile file,
            @RequestParam UUID reportingPeriodId,
            @RequestParam(required = false) UUID organizationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {

        UploadOutcome outcome = uploads.upload(new UploadImportCommand(
                PeriodId.of(reportingPeriodId),
                organizationId == null ? null : OrganizationId.of(organizationId),
                idempotencyKey,
                file.getOriginalFilename(),
                file.getContentType(),
                openStream(file)));

        ImportJobResponse body = ImportJobResponse.from(outcome.job());
        // A replayed key is not a new import, so it does not answer 201.
        return outcome.created()
                ? ResponseEntity.created(URI.create("/api/v1/imports/" + body.id()))
                        .body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping
    @Operation(
            operationId = "listImports",
            summary = "List imports the caller may see",
            description =
                    "Administrators see every import; everyone else sees their own and their" + " organizations'.")
    public PageResponse<ImportJobResponse> list(
            @RequestParam(required = false) UUID reportingPeriodId,
            @RequestParam(required = false) ImportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        ImportJobRepository.ImportPage result =
                queries.search(reportingPeriodId == null ? null : PeriodId.of(reportingPeriodId), status, page, size);
        return PageResponse.of(
                result.content().stream().map(ImportJobResponse::from).toList(), page, size, result.totalElements());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getImport", summary = "Get one import job")
    public ImportJobResponse get(@PathVariable UUID id) {
        return ImportJobResponse.from(queries.require(ImportJobId.of(id)));
    }

    @GetMapping("/{id}/errors")
    @Operation(
            operationId = "getImportErrors",
            summary = "The rows this import could not apply",
            description = "A rule that invalidated a whole organization's rows appears once, with the"
                    + " number of rows it cost.")
    public List<ImportRowErrorResponse> errors(@PathVariable UUID id) {
        return queries.errorsOf(ImportJobId.of(id)).stream()
                .map(ImportRowErrorResponse::from)
                .toList();
    }

    private static java.io.InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
