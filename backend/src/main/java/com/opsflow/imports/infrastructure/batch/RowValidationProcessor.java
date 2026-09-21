/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.imports.infrastructure.batch;

import com.opsflow.organization.OrganizationCode;
import com.opsflow.organization.OrganizationDirectory;
import com.opsflow.organization.OrganizationId;
import com.opsflow.organization.OrganizationSummary;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodQuery;
import com.opsflow.reporting.ScopedMetric;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Resolves codes and parses numbers so a rejection can name the row and the code that caused it.
 * It deliberately does not judge whether a value is acceptable: that rule belongs to the module
 * that owns submissions, and two copies of it would drift.
 */
class RowValidationProcessor implements ItemProcessor<RawRow, ParsedRow> {

    private final OrganizationDirectory organizations;
    private final OrganizationId scopeOrganizationId;
    private final Map<String, MetricId> metricsInScope;
    private final Map<String, Optional<OrganizationSummary>> resolvedOrganizations = new HashMap<>();

    RowValidationProcessor(
            OrganizationDirectory organizations,
            PeriodQuery periods,
            PeriodId reportingPeriodId,
            OrganizationId scopeOrganizationId) {
        this.organizations = organizations;
        this.scopeOrganizationId = scopeOrganizationId;
        this.metricsInScope = periods.scopeOf(reportingPeriodId).stream()
                .collect(Collectors.toMap(metric -> metric.code().value(), ScopedMetric::metricId));
    }

    @Override
    public ParsedRow process(RawRow row) {
        if (row.parseError() != null) {
            return ParsedRow.rejected(row.rowNumber(), "ROW_MALFORMED", row.parseError());
        }

        String organizationCode = trim(row.organizationCode());
        String metricCode = trim(row.metricCode());
        if (organizationCode.isEmpty() || metricCode.isEmpty()) {
            return ParsedRow.rejected(
                    row.rowNumber(), "ROW_INCOMPLETE", "Both organization_code and metric_code are required");
        }

        Optional<OrganizationSummary> organization = resolve(organizationCode);
        if (organization.isEmpty()) {
            return ParsedRow.rejected(row.rowNumber(), "ORG_UNKNOWN", "No organization has code " + organizationCode);
        }
        if (scopeOrganizationId != null && !organization.get().id().equals(scopeOrganizationId)) {
            return ParsedRow.rejected(
                    row.rowNumber(),
                    "ORG_OUT_OF_SCOPE",
                    "This import is scoped to one organization and " + organizationCode + " is not it");
        }

        MetricId metricId = metricsInScope.get(metricCode.toUpperCase(java.util.Locale.ROOT));
        if (metricId == null) {
            return ParsedRow.rejected(
                    row.rowNumber(), "METRIC_UNKNOWN", "No metric " + metricCode + " is collected for this period");
        }

        BigDecimal value;
        try {
            value = new BigDecimal(trim(row.value()));
        } catch (NumberFormatException | NullPointerException ex) {
            return ParsedRow.rejected(row.rowNumber(), "VALUE_NOT_NUMERIC", "\"" + row.value() + "\" is not a number");
        }

        String note = trim(row.note());
        return ParsedRow.resolved(
                row.rowNumber(),
                organization.get().id(),
                metricId,
                value,
                note.isEmpty() ? null : note.length() <= 500 ? note : note.substring(0, 500));
    }

    private Optional<OrganizationSummary> resolve(String code) {
        return resolvedOrganizations.computeIfAbsent(code, value -> {
            try {
                return organizations.findByCode(new OrganizationCode(value));
            } catch (IllegalArgumentException malformed) {
                return Optional.empty();
            }
        });
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
