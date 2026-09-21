/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api;

import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.web.PageResponse;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.api.dto.CreatePeriodRequest;
import com.opsflow.reporting.api.dto.ParticipantResponse;
import com.opsflow.reporting.api.dto.PeriodDetailResponse;
import com.opsflow.reporting.api.dto.PeriodResponse;
import com.opsflow.reporting.api.dto.ReschedulePeriodRequest;
import com.opsflow.reporting.api.dto.ScopedMetricResponse;
import com.opsflow.reporting.api.dto.SetMetricScopeRequest;
import com.opsflow.reporting.api.dto.SetParticipantsRequest;
import com.opsflow.reporting.api.dto.VersionedRequest;
import com.opsflow.reporting.application.PeriodCommandService;
import com.opsflow.reporting.application.PeriodQueryService;
import com.opsflow.reporting.domain.ReportingPeriodRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/periods")
@Validated
@Tag(name = "Reporting periods", description = "Reporting periods and their scope")
public class PeriodController {

    private final PeriodCommandService commands;
    private final PeriodQueryService queries;

    public PeriodController(PeriodCommandService commands, PeriodQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "List reporting periods")
    public PageResponse<PeriodResponse> list(
            @RequestParam(required = false) PeriodStatus status,
            @RequestParam(required = false) PeriodType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        ReportingPeriodRepository.PeriodPage result = queries.search(status, type, page, size);
        return PageResponse.of(
                result.content().stream().map(PeriodResponse::from).toList(), page, size, result.totalElements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one period with its metric and organization scope")
    public PeriodDetailResponse get(@PathVariable UUID id) {
        PeriodId periodId = PeriodId.of(id);
        return new PeriodDetailResponse(
                PeriodResponse.from(queries.require(periodId)),
                queries.scopeOf(periodId).stream()
                        .map(ScopedMetricResponse::from)
                        .toList(),
                queries.participantsOf(periodId).stream()
                        .map(ParticipantResponse::from)
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a reporting period")
    public ResponseEntity<PeriodResponse> create(@Valid @RequestBody CreatePeriodRequest request) {
        PeriodResponse created = PeriodResponse.from(commands.create(
                new PeriodCode(request.code()),
                request.periodType(),
                request.startsOn(),
                request.endsOn(),
                request.submissionDeadline()));
        return ResponseEntity.created(URI.create("/api/v1/periods/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reschedule a draft period")
    public PeriodResponse reschedule(@PathVariable UUID id, @Valid @RequestBody ReschedulePeriodRequest request) {
        return PeriodResponse.from(commands.reschedule(
                PeriodId.of(id),
                request.startsOn(),
                request.endsOn(),
                request.submissionDeadline(),
                request.version()));
    }

    @PutMapping("/{id}/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set the metric scope", description = "Draft periods only; the scope freezes on open.")
    public PeriodResponse setMetricScope(@PathVariable UUID id, @Valid @RequestBody SetMetricScopeRequest request) {
        List<PeriodCommandService.MetricScopeEntry> entries = request.metrics().stream()
                .map(entry -> new PeriodCommandService.MetricScopeEntry(
                        com.opsflow.reporting.MetricId.of(entry.metricId()), entry.required(), entry.displayOrder()))
                .toList();
        return PeriodResponse.from(commands.replaceMetricScope(PeriodId.of(id), entries, request.version()));
    }

    @PutMapping("/{id}/organizations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Set the participating organizations",
            description = "Draft periods only; participation freezes on open.")
    public PeriodResponse setParticipants(@PathVariable UUID id, @Valid @RequestBody SetParticipantsRequest request) {
        Set<OrganizationId> organizationIds = request.organizationIds().stream()
                .map(OrganizationId::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return PeriodResponse.from(commands.replaceParticipants(PeriodId.of(id), organizationIds, request.version()));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Open a period", description = "Requires both scopes to be non-empty, and freezes them.")
    public PeriodResponse open(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return PeriodResponse.from(commands.open(PeriodId.of(id), request.version()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close a period")
    public PeriodResponse close(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return PeriodResponse.from(commands.close(PeriodId.of(id), request.version()));
    }
}
