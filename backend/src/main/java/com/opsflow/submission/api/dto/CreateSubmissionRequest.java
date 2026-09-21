/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSubmissionRequest(@NotNull UUID organizationId, @NotNull UUID reportingPeriodId) {}
