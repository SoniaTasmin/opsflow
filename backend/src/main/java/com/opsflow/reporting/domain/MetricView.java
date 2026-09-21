/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.ValueType;

public record MetricView(
        MetricId id,
        MetricCode code,
        String name,
        String description,
        String unit,
        ValueType valueType,
        NumericRange range,
        boolean active,
        long version) {}
