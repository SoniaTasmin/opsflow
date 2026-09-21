/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.correlation.CorrelationId;
import com.opsflow.reporting.PeriodId;
import java.util.List;

/** One organization's worth of imported values, already deduplicated by the caller. */
public record ApplyValuesCommand(
        OrganizationId organizationId,
        PeriodId reportingPeriodId,
        List<ImportedValue> values,
        ImportAuthority authority,
        CorrelationId correlationId) {

    public ApplyValuesCommand {
        values = List.copyOf(values);
    }
}
