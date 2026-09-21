/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.domain;

import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MetricRepository {

    Optional<MetricDefinition> findById(MetricId id);

    boolean existsByCode(MetricCode code);

    MetricDefinition insert(MetricDefinition metric, com.opsflow.identity.UserId createdBy);

    MetricDefinition update(MetricDefinition metric, long expectedVersion, com.opsflow.identity.UserId updatedBy);

    Optional<MetricView> viewById(MetricId id);

    Optional<MetricView> viewByCode(MetricCode code);

    List<MetricView> viewsByCode(Set<MetricCode> codes);

    List<MetricView> viewsById(Set<MetricId> ids);

    MetricPage search(String term, Boolean active, int page, int size);

    /** True once the metric is in the scope of a period that has left DRAFT. */
    boolean isUsedByNonDraftPeriod(MetricId id);

    boolean isUsedByOpenPeriod(MetricId id);

    record MetricPage(List<MetricView> content, long totalElements) {}
}
