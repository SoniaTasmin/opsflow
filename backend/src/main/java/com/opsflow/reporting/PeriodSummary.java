/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import java.time.LocalDate;

public record PeriodSummary(
        PeriodId id,
        PeriodCode code,
        PeriodType periodType,
        LocalDate startsOn,
        LocalDate endsOn,
        LocalDate submissionDeadline,
        PeriodStatus status) {

    public boolean isOpen() {
        return status == PeriodStatus.OPEN;
    }
}
