/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.application;

import com.opsflow.platform.error.NotFoundException;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.domain.MetricRepository;
import com.opsflow.reporting.domain.MetricView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricQueryService {

    private final MetricRepository metrics;

    public MetricQueryService(MetricRepository metrics) {
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public MetricRepository.MetricPage search(String term, Boolean active, int page, int size) {
        return metrics.search(term, active, page, size);
    }

    @Transactional(readOnly = true)
    public MetricView require(MetricId id) {
        return metrics.viewById(id).orElseThrow(() -> NotFoundException.of("Metric", id));
    }
}
