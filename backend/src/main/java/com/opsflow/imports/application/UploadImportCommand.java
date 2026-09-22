/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.application;

import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.io.InputStream;

/**
 * @param organizationId null when the file may carry rows for several organizations
 * @param originalFilename data only: storage names come from the job id
 */
public record UploadImportCommand(
        PeriodId reportingPeriodId,
        OrganizationId organizationId,
        String idempotencyKey,
        String originalFilename,
        String contentType,
        InputStream content) {}
