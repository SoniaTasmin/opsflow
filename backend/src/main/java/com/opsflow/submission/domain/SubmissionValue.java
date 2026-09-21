/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.reporting.MetricId;
import com.opsflow.submission.ValueSource;
import java.math.BigDecimal;

public record SubmissionValue(MetricId metricId, BigDecimal value, String note, ValueSource source) {

    public SubmissionValue {
        if (metricId == null) {
            throw new IllegalArgumentException("metricId is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
