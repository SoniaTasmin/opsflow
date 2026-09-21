/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import java.time.Instant;
import java.time.LocalDate;

public record PeriodView(
        PeriodId id,
        PeriodCode code,
        PeriodType periodType,
        LocalDate startsOn,
        LocalDate endsOn,
        LocalDate submissionDeadline,
        PeriodStatus status,
        Instant openedAt,
        Instant closedAt,
        int metricCount,
        int participantCount,
        long version) {}
