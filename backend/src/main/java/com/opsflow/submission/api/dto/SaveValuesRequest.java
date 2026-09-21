/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A complete replacement of the submission's values: a metric left out is cleared. The value
 * form posts every metric in scope, so there is no partial-save case to merge.
 */
public record SaveValuesRequest(@NotNull @Valid List<SubmissionValueEntry> values, @PositiveOrZero long version) {

    public record SubmissionValueEntry(
            @NotNull UUID metricId, @NotNull BigDecimal value, @Size(max = 500) String note) {}
}
