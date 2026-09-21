/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.application;

import com.opsflow.identity.CurrentUserProvider;
import com.opsflow.identity.UserId;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.domain.MetricDefinition;
import com.opsflow.reporting.domain.MetricRepository;
import com.opsflow.reporting.domain.MetricView;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricCommandService {

    private final MetricRepository metrics;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public MetricCommandService(MetricRepository metrics, CurrentUserProvider currentUser, Clock clock) {
        this.metrics = metrics;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public MetricView create(
            MetricCode code, String name, String description, String unit, ValueType valueType, NumericRange range) {
        if (metrics.existsByCode(code)) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "A metric with this code already exists",
                    Map.of("metricCode", code.value()));
        }
        MetricDefinition metric =
                MetricDefinition.create(MetricId.generate(clock), code, name, description, unit, valueType, range);
        metrics.insert(metric, actorId());
        return metrics.viewById(metric.id()).orElseThrow();
    }

    @Transactional
    public MetricView update(
            MetricId id, String name, String description, String unit, NumericRange range, long expectedVersion) {
        MetricDefinition metric = load(id);
        metric.describe(name, description, unit);
        if (range != null && !range.equals(metric.range())) {
            metric.changeRange(range, metrics.isUsedByNonDraftPeriod(id));
        }
        metrics.update(metric, expectedVersion, actorId());
        return metrics.viewById(id).orElseThrow();
    }

    @Transactional
    public MetricView archive(MetricId id, long expectedVersion) {
        MetricDefinition metric = load(id);
        metric.archive(metrics.isUsedByOpenPeriod(id));
        metrics.update(metric, expectedVersion, actorId());
        return metrics.viewById(id).orElseThrow();
    }

    private MetricDefinition load(MetricId id) {
        return metrics.findById(id).orElseThrow(() -> NotFoundException.of("Metric", id));
    }

    private UserId actorId() {
        return currentUser.currentUserId().orElse(null);
    }
}
