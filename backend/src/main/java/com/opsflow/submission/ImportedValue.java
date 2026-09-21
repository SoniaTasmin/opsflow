/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission;

import com.opsflow.reporting.MetricId;
import java.math.BigDecimal;

/**
 * @param rowNumber the line in the source file, carried so a rejection can name it
 */
public record ImportedValue(int rowNumber, MetricId metricId, BigDecimal value, String note) {}
