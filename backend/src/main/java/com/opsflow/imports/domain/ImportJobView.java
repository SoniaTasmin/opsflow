/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.domain;

import com.opsflow.identity.UserId;
import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.ImportStatus;
import com.opsflow.organization.OrganizationId;
import com.opsflow.reporting.PeriodId;
import java.time.Instant;

public record ImportJobView(
        ImportJobId id,
        PeriodId reportingPeriodId,
        OrganizationId organizationId,
        UserId initiatedBy,
        ImportStatus status,
        String originalFilename,
        long fileSizeBytes,
        int submittedRows,
        int processedRows,
        int validRows,
        int invalidRows,
        ImportFailure failure,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        long version) {}
