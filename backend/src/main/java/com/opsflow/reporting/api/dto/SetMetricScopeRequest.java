/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record SetMetricScopeRequest(@NotEmpty @Valid List<MetricScopeEntry> metrics, @PositiveOrZero long version) {

    // Named rather than left as a generic "Entry": the OpenAPI schema is keyed by the simple
    // name, so two nested Entry records in different requests would collapse into one schema
    // and the generated client would type one of them wrongly.
    public record MetricScopeEntry(@NotNull UUID metricId, boolean required, @PositiveOrZero int displayOrder) {}
}
