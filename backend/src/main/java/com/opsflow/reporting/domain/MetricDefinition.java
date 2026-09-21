/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.ValueType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MetricDefinition {

    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 120;
    private static final int DESCRIPTION_MAX = 500;
    private static final int UNIT_MAX = 32;
    private static final BigDecimal PERCENTAGE_MIN = BigDecimal.ZERO;
    private static final BigDecimal PERCENTAGE_MAX = BigDecimal.valueOf(100);

    private final MetricId id;
    private final MetricCode code;
    private final ValueType valueType;
    private String name;
    private String description;
    private String unit;
    private NumericRange range;
    private boolean active;
    private final long version;

    private MetricDefinition(
            MetricId id,
            MetricCode code,
            String name,
            String description,
            String unit,
            ValueType valueType,
            NumericRange range,
            boolean active,
            long version) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.code = Objects.requireNonNull(code, "code is required");
        this.valueType = Objects.requireNonNull(valueType, "valueType is required");
        this.name = requireText(name, "name", NAME_MIN, NAME_MAX);
        this.description = optionalText(description, DESCRIPTION_MAX);
        this.unit = requireText(unit, "unit", 1, UNIT_MAX);
        this.range = requireCompatibleRange(valueType, range);
        this.active = active;
        this.version = version;
    }

    public static MetricDefinition create(
            MetricId id,
            MetricCode code,
            String name,
            String description,
            String unit,
            ValueType valueType,
            NumericRange range) {
        return new MetricDefinition(id, code, name, description, unit, valueType, range, true, 0L);
    }

    public static MetricDefinition rehydrate(
            MetricId id,
            MetricCode code,
            String name,
            String description,
            String unit,
            ValueType valueType,
            NumericRange range,
            boolean active,
            long version) {
        return new MetricDefinition(id, code, name, description, unit, valueType, range, active, version);
    }

    public void describe(String newName, String newDescription, String newUnit) {
        assertActive();
        this.name = requireText(newName, "name", NAME_MIN, NAME_MAX);
        this.description = optionalText(newDescription, DESCRIPTION_MAX);
        this.unit = requireText(newUnit, "unit", 1, UNIT_MAX);
    }

    /**
     * Narrowing bounds on a metric that is already being collected would retroactively
     * invalidate values that were legal when submitted, so {@code inUse} forbids it.
     */
    public void changeRange(NumericRange newRange, boolean inUse) {
        assertActive();
        if (inUse) {
            throw new BusinessRuleException(
                    "METRIC_IN_USE",
                    "Bounds cannot be changed while the metric is in the scope of an open or closed period",
                    Map.of("metricId", id.toString()));
        }
        this.range = requireCompatibleRange(valueType, newRange);
    }

    /**
     * An open period's scope is frozen, so archiving a metric it collects would leave it
     * requiring something the catalogue says no longer exists. Draft periods can still drop the
     * metric before they open, and closed periods are history.
     */
    public void archive(boolean usedByOpenPeriod) {
        if (usedByOpenPeriod) {
            throw new BusinessRuleException(
                    "METRIC_IN_USE",
                    "The metric is collected by an open reporting period",
                    Map.of("metricId", id.toString()));
        }
        if (!active) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Metric is already archived",
                    Map.of("metricId", id.toString()));
        }
        this.active = false;
    }

    private void assertActive() {
        if (!active) {
            throw new BusinessRuleException(
                    "METRIC_ARCHIVED", "Cannot modify an archived metric", Map.of("metricId", id.toString()));
        }
    }

    private static NumericRange requireCompatibleRange(ValueType valueType, NumericRange range) {
        NumericRange effective = range == null ? NumericRange.UNBOUNDED : range;
        if (valueType == ValueType.PERCENTAGE) {
            BigDecimal min = effective.minimum().orElse(PERCENTAGE_MIN);
            BigDecimal max = effective.maximum().orElse(PERCENTAGE_MAX);
            if (min.compareTo(PERCENTAGE_MIN) < 0 || max.compareTo(PERCENTAGE_MAX) > 0) {
                throw new BusinessRuleException(
                        "METRIC_RANGE_INVALID",
                        "A percentage metric must stay within 0-100",
                        Map.of("minValue", min.toString(), "maxValue", max.toString()));
            }
            return new NumericRange(min, max);
        }
        return effective;
    }

    private static String requireText(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new IllegalArgumentException("Metric " + field + " must be " + min + "-" + max + " characters");
        }
        return trimmed;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Metric description must be at most " + max + " characters");
        }
        return trimmed;
    }

    public MetricId id() {
        return id;
    }

    public MetricCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public String unit() {
        return unit;
    }

    public ValueType valueType() {
        return valueType;
    }

    public NumericRange range() {
        return range;
    }

    public boolean isActive() {
        return active;
    }

    public long version() {
        return version;
    }
}
