/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

/** A metric in a period's scope, with everything a submission needs to validate a value. */
public record ScopedMetric(
        MetricId metricId,
        MetricCode code,
        String name,
        String unit,
        ValueType valueType,
        NumericRange range,
        boolean required,
        int displayOrder) {

    /** What a value must actually satisfy: the declared range narrowed by the type's own bounds. */
    public NumericRange acceptedRange() {
        return range.intersect(valueType.impliedRange());
    }
}
