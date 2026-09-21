/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api;

import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.web.PageResponse;
import com.opsflow.platform.web.VersionedRequest;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.api.dto.CreateSubmissionRequest;
import com.opsflow.submission.api.dto.RejectSubmissionRequest;
import com.opsflow.submission.api.dto.SaveValuesRequest;
import com.opsflow.submission.api.dto.SubmissionDetailResponse;
import com.opsflow.submission.api.dto.SubmissionResponse;
import com.opsflow.submission.api.dto.TransitionResponse;
import com.opsflow.submission.application.SubmissionCommandService;
import com.opsflow.submission.application.SubmissionQueryService;
import com.opsflow.submission.domain.Submission.ValueEntry;
import com.opsflow.submission.domain.SubmissionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/submissions", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Submissions", description = "Reporting submissions and their review workflow")
public class SubmissionController {

    private final SubmissionCommandService commands;
    private final SubmissionQueryService queries;

    public SubmissionController(SubmissionCommandService commands, SubmissionQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    @Operation(
            operationId = "listSubmissions",
            summary = "List submissions",
            description = "Scoped to the caller's organizations unless they are a reviewer or an administrator."
                    + " Filtering by status SUBMITTED is the review queue.")
    public PageResponse<SubmissionResponse> list(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID reportingPeriodId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        SubmissionQueryService.Page result = queries.search(
                organizationId == null ? null : OrganizationId.of(organizationId),
                reportingPeriodId == null ? null : PeriodId.of(reportingPeriodId),
                status,
                page,
                size);
        return PageResponse.of(
                result.content().stream().map(SubmissionResponse::from).toList(), page, size, result.totalElements());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSubmission", summary = "Get one submission with its metric scope and values")
    public SubmissionDetailResponse get(@PathVariable UUID id) {
        return SubmissionDetailResponse.from(queries.detail(SubmissionId.of(id)));
    }

    @GetMapping("/{id}/history")
    @Operation(operationId = "getSubmissionHistory", summary = "The submission's workflow history")
    public List<TransitionResponse> history(@PathVariable UUID id) {
        return queries.history(SubmissionId.of(id)).stream()
                .map(TransitionResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(operationId = "createSubmission", summary = "Start a submission for an organization and period")
    public ResponseEntity<SubmissionDetailResponse> create(@Valid @RequestBody CreateSubmissionRequest request) {
        SubmissionView created =
                commands.create(OrganizationId.of(request.organizationId()), PeriodId.of(request.reportingPeriodId()));
        SubmissionDetailResponse body = respond(created);
        return ResponseEntity.created(URI.create("/api/v1/submissions/" + body.id()))
                .body(body);
    }

    @PutMapping("/{id}/values")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
            operationId = "saveSubmissionValues",
            summary = "Replace the submission's values",
            description = "Drafts and rejected submissions only. A metric left out of the request is cleared.")
    public SubmissionDetailResponse saveValues(@PathVariable UUID id, @Valid @RequestBody SaveValuesRequest request) {
        List<ValueEntry> entries = request.values().stream()
                .map(entry -> new ValueEntry(MetricId.of(entry.metricId()), entry.value(), entry.note()))
                .toList();
        return respond(commands.saveValues(SubmissionId.of(id), entries, request.version()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(operationId = "submitSubmission", summary = "Send the submission for review")
    public SubmissionDetailResponse submit(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return respond(commands.submit(SubmissionId.of(id), request.version()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(operationId = "withdrawSubmission", summary = "Pull a submission back into draft")
    public SubmissionDetailResponse withdraw(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return respond(commands.withdraw(SubmissionId.of(id), request.version()));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    @Operation(operationId = "claimSubmission", summary = "Take a submission out of the review queue")
    public SubmissionDetailResponse claim(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return respond(commands.claim(SubmissionId.of(id), request.version()));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    @Operation(operationId = "releaseSubmission", summary = "Return a claimed submission to the queue")
    public SubmissionDetailResponse release(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return respond(commands.release(SubmissionId.of(id), request.version()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    @Operation(operationId = "approveSubmission", summary = "Approve a submission under review")
    public SubmissionDetailResponse approve(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return respond(commands.approve(SubmissionId.of(id), request.version()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    @Operation(
            operationId = "rejectSubmission",
            summary = "Reject a submission under review",
            description = "The reason is required and is shown to the author.")
    public SubmissionDetailResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectSubmissionRequest request) {
        return respond(commands.reject(SubmissionId.of(id), request.reason(), request.version()));
    }

    /**
     * Commands answer with the whole submission rather than an acknowledgement: every one of
     * them changes what the detail screen may show next, and a client that has to re-fetch to
     * find out is a client that renders a stale version number into its next request.
     */
    private SubmissionDetailResponse respond(SubmissionView view) {
        return SubmissionDetailResponse.from(queries.detail(view.id()));
    }
}
