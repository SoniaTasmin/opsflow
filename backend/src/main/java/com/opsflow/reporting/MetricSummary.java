/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

public record MetricSummary(
        MetricId id,
        MetricCode code,
        String name,
        String unit,
        ValueType valueType,
        NumericRange range,
        boolean active) {}
