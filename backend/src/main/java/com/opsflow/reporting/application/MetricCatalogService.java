/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.application;

import com.opsflow.reporting.MetricCatalog;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.MetricSummary;
import com.opsflow.reporting.domain.MetricRepository;
import com.opsflow.reporting.domain.MetricView;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MetricCatalogService implements MetricCatalog {

    private final MetricRepository metrics;

    MetricCatalogService(MetricRepository metrics) {
        this.metrics = metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MetricSummary> findById(MetricId id) {
        return metrics.viewById(id).map(MetricCatalogService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MetricSummary> findByCode(MetricCode code) {
        return metrics.viewByCode(code).map(MetricCatalogService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetricSummary> findAllByCode(Set<MetricCode> codes) {
        return metrics.viewsByCode(codes).stream()
                .map(MetricCatalogService::toSummary)
                .toList();
    }

    private static MetricSummary toSummary(MetricView view) {
        return new MetricSummary(
                view.id(), view.code(), view.name(), view.unit(), view.valueType(), view.range(), view.active());
    }
}
