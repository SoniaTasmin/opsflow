/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.imports.ImportJobId;
import com.opsflow.imports.application.ImportJobLifecycle;
import com.opsflow.imports.domain.ImportRowError;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.correlation.CorrelationId;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.ApplyValuesCommand;
import com.opsflow.submission.ApplyValuesResult;
import com.opsflow.submission.ImportAuthority;
import com.opsflow.submission.ImportedValue;
import com.opsflow.submission.SubmissionImportService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Groups a chunk by organization and hands each group to the submission module. Efficiency comes
 * from the grouping; the aggregate is never bypassed.
 */
class SubmissionApplyWriter implements ItemWriter<ParsedRow> {

    private final SubmissionImportService submissions;
    private final ImportJobLifecycle lifecycle;
    private final ImportJobId jobId;
    private final PeriodId reportingPeriodId;
    private final ImportAuthority authority;
    private final CorrelationId correlationId;

    SubmissionApplyWriter(
            SubmissionImportService submissions,
            ImportJobLifecycle lifecycle,
            ImportJobId jobId,
            PeriodId reportingPeriodId,
            ImportAuthority authority,
            CorrelationId correlationId) {
        this.submissions = submissions;
        this.lifecycle = lifecycle;
        this.jobId = jobId;
        this.reportingPeriodId = reportingPeriodId;
        this.authority = authority;
        this.correlationId = correlationId;
    }

    @Override
    public void write(Chunk<? extends ParsedRow> chunk) {
        List<ImportRowError> errors = new ArrayList<>();
        int valid = 0;
        int invalid = 0;

        Map<OrganizationId, Map<MetricId, ParsedRow>> byOrganization = new LinkedHashMap<>();
        for (ParsedRow row : chunk) {
            if (row.isRejected()) {
                errors.add(ImportRowError.row(row.rowNumber(), row.rejectionCode(), row.rejectionMessage()));
                invalid++;
                continue;
            }
            Map<MetricId, ParsedRow> forOrganization =
                    byOrganization.computeIfAbsent(row.organizationId(), key -> new LinkedHashMap<>());
            ParsedRow superseded = forOrganization.put(row.metricId(), row);
            if (superseded != null) {
                // Last value wins, which is also what re-uploading a corrected file does.
                errors.add(ImportRowError.row(
                        superseded.rowNumber(),
                        "DUPLICATE_ROW",
                        "A later row in this file reports the same metric for the same organization"));
                invalid++;
            }
        }

        for (var group : byOrganization.entrySet()) {
            List<ParsedRow> rows = List.copyOf(group.getValue().values());
            ApplyValuesResult result = submissions.applyValues(new ApplyValuesCommand(
                    group.getKey(),
                    reportingPeriodId,
                    rows.stream()
                            .map(row -> new ImportedValue(row.rowNumber(), row.metricId(), row.value(), row.note()))
                            .toList(),
                    authority,
                    correlationId));

            if (result.groupRejection() != null) {
                // One error for the whole group: a rule that invalidates every row would
                // otherwise bury the rows the user can actually fix.
                errors.add(new ImportRowError(
                        rows.getFirst().rowNumber(),
                        result.groupRejection().code(),
                        result.groupRejection().message() + " (" + rows.size() + " rows skipped)",
                        rows.size()));
                invalid += rows.size();
                continue;
            }

            valid += result.appliedCount();
            for (var rejected : result.rejected()) {
                errors.add(ImportRowError.row(rejected.rowNumber(), rejected.code(), rejected.message()));
                invalid++;
            }
        }

        lifecycle.recordChunk(jobId, chunk.size(), valid, invalid, errors);
    }
}
