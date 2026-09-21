/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportingPeriodJpaRepository extends JpaRepository<ReportingPeriodEntity, UUID> {
    boolean existsByCode(String code);
}
