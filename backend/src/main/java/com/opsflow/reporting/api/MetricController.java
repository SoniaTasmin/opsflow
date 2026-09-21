/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api;

import com.opsflow.platform.web.PageResponse;
import com.opsflow.platform.web.VersionedRequest;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.api.dto.CreateMetricRequest;
import com.opsflow.reporting.api.dto.MetricResponse;
import com.opsflow.reporting.api.dto.UpdateMetricRequest;
import com.opsflow.reporting.application.MetricCommandService;
import com.opsflow.reporting.application.MetricQueryService;
import com.opsflow.reporting.domain.MetricRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Metrics", description = "The metric catalogue")
public class MetricController {

    private final MetricCommandService commands;
    private final MetricQueryService queries;

    public MetricController(MetricCommandService commands, MetricQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    @Operation(operationId = "listMetrics", summary = "List metrics")
    public PageResponse<MetricResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        MetricRepository.MetricPage result = queries.search(q, active, page, size);
        return PageResponse.of(
                result.content().stream().map(MetricResponse::from).toList(), page, size, result.totalElements());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getMetric", summary = "Get one metric")
    public MetricResponse get(@PathVariable UUID id) {
        return MetricResponse.from(queries.require(MetricId.of(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "createMetric", summary = "Create a metric")
    public ResponseEntity<MetricResponse> create(@Valid @RequestBody CreateMetricRequest request) {
        MetricResponse created = MetricResponse.from(commands.create(
                new MetricCode(request.code()),
                request.name(),
                request.description(),
                request.unit(),
                request.valueType(),
                new NumericRange(request.minValue(), request.maxValue())));
        return ResponseEntity.created(URI.create("/api/v1/metrics/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "updateMetric",
            summary = "Update a metric",
            description = "Code and value type cannot be changed.")
    public MetricResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMetricRequest request) {
        return MetricResponse.from(commands.update(
                MetricId.of(id),
                request.name(),
                request.description(),
                request.unit(),
                new NumericRange(request.minValue(), request.maxValue()),
                request.version()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "archiveMetric",
            summary = "Archive a metric",
            description = "Historical values keep resolving; there is no delete.")
    public MetricResponse archive(@PathVariable UUID id, @Valid @RequestBody VersionedRequest request) {
        return MetricResponse.from(commands.archive(MetricId.of(id), request.version()));
    }
}
