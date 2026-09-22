/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.domain.ImportJob;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.correlation.CorrelationId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.ImportAuthority;

/**
 * The authority comes from the job row, recorded at admission from a verified token: a batch
 * thread has no security context and must not invent one.
 */
record ImportContext(
        ImportJobId jobId,
        PeriodId reportingPeriodId,
        OrganizationId scopeOrganizationId,
        String storageRef,
        ImportAuthority authority,
        CorrelationId correlationId) {

    static ImportContext of(ImportJob job, CorrelationId correlationId) {
        return new ImportContext(
                job.id(),
                job.reportingPeriodId(),
                job.organizationId().orElse(null),
                job.storageRef(),
                new ImportAuthority(job.initiatedBy(), job.authorizationMode()),
                correlationId);
    }
}
