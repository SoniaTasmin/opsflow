/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.reporting.MetricId;

public record PeriodMetric(MetricId metricId, boolean required, int displayOrder) {

    public PeriodMetric {
        if (metricId == null) {
            throw new IllegalArgumentException("metricId is required");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
    }
}
