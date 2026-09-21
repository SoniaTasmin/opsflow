/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MetricCatalog {

    Optional<MetricSummary> findById(MetricId id);

    Optional<MetricSummary> findByCode(MetricCode code);

    List<MetricSummary> findAllByCode(Set<MetricCode> codes);
}
