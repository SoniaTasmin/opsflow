/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record SetMetricScopeRequest(@NotEmpty @Valid List<Entry> metrics, @PositiveOrZero long version) {

    public record Entry(@NotNull UUID metricId, boolean required, @PositiveOrZero int displayOrder) {}
}
