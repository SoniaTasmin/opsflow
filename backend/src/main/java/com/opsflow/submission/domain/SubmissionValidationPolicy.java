/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.domain;

import com.opsflow.platform.error.BusinessRuleException;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.ScopedMetric;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single place a value is checked against the metric it reports.
 *
 * <p>Manual editing and CSV import both run through this, so a value a reviewer sees can never
 * have entered by a route with weaker rules.
 */
public final class SubmissionValidationPolicy {

    /** Matches numeric(18,4) in the schema: rounding a user's number silently is not acceptable. */
    private static final int MAX_SCALE = 4;

    private final Map<MetricId, ScopedMetric> scope;

    public SubmissionValidationPolicy(List<ScopedMetric> scopedMetrics) {
        this.scope = scopedMetrics.stream()
                .collect(Collectors.toMap(ScopedMetric::metricId, metric -> metric, (a, b) -> a, LinkedHashMap::new));
    }

    public ScopedMetric require(MetricId metricId) {
        ScopedMetric metric = scope.get(metricId);
        if (metric == null) {
            throw new BusinessRuleException(
                    "METRIC_NOT_IN_SCOPE",
                    "This metric is not collected for this reporting period",
                    Map.of("metricId", metricId.toString()));
        }
        return metric;
    }

    public void validate(MetricId metricId, BigDecimal value) {
        ScopedMetric metric = require(metricId);

        if (value.stripTrailingZeros().scale() > MAX_SCALE) {
            throw new BusinessRuleException(
                    "VALUE_SCALE_EXCEEDED",
                    "A value may carry at most " + MAX_SCALE + " decimal places",
                    context(metric, value));
        }
        if (!metric.valueType().accepts(value)) {
            throw new BusinessRuleException(
                    "VALUE_NOT_INTEGER", "This metric accepts whole numbers only", context(metric, value));
        }
        if (!metric.acceptedRange().contains(value)) {
            throw new BusinessRuleException(
                    "VALUE_OUT_OF_RANGE",
                    "This value falls outside the metric's permitted range",
                    context(metric, value));
        }
    }

    public Set<MetricId> requiredMetrics() {
        return scope.values().stream()
                .filter(ScopedMetric::required)
                .map(ScopedMetric::metricId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isEmpty() {
        return scope.isEmpty();
    }

    private static Map<String, Object> context(ScopedMetric metric, BigDecimal value) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("metricId", metric.metricId().toString());
        context.put("metricCode", metric.code().value());
        context.put("value", value.toPlainString());
        metric.acceptedRange().minimum().ifPresent(min -> context.put("minValue", min.toPlainString()));
        metric.acceptedRange().maximum().ifPresent(max -> context.put("maxValue", max.toPlainString()));
        return context;
    }
}
