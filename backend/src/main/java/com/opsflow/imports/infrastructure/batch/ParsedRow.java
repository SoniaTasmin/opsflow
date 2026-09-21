/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.MetricId;
import java.math.BigDecimal;

/**
 * A line after the imports module has done what it can: resolved codes and parsed the number.
 * Whether the value is acceptable is the submission module's answer, not this one's.
 */
record ParsedRow(
        int rowNumber,
        OrganizationId organizationId,
        MetricId metricId,
        BigDecimal value,
        String note,
        String rejectionCode,
        String rejectionMessage) {

    static ParsedRow resolved(
            int rowNumber, OrganizationId organizationId, MetricId metricId, BigDecimal value, String note) {
        return new ParsedRow(rowNumber, organizationId, metricId, value, note, null, null);
    }

    static ParsedRow rejected(int rowNumber, String code, String message) {
        return new ParsedRow(rowNumber, null, null, null, null, code, message);
    }

    boolean isRejected() {
        return rejectionCode != null;
    }
}
