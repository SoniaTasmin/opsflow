/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.PeriodCode;
import com.opsflow.reporting.PeriodId;
import com.opsflow.reporting.PeriodStatus;
import com.opsflow.reporting.PeriodType;
import com.opsflow.reporting.ScopedMetric;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.domain.PeriodMetric;
import com.opsflow.reporting.domain.PeriodView;
import com.opsflow.reporting.domain.ReportingPeriod;
import com.opsflow.reporting.domain.ReportingPeriodRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaReportingPeriodRepository implements ReportingPeriodRepository {

    private static final String SELECT_VIEW =
            """
            SELECT p.id, p.code, p.period_type, p.starts_on, p.ends_on, p.submission_deadline,
                   p.status, p.opened_at, p.closed_at, p.version,
                   (SELECT count(*) FROM opsflow.reporting_period_metric pm
                     WHERE pm.reporting_period_id = p.id) AS metric_count,
                   (SELECT count(*) FROM opsflow.reporting_period_organization po
                     WHERE po.reporting_period_id = p.id) AS participant_count
              FROM opsflow.reporting_period p
            """;

    private final ReportingPeriodJpaRepository periods;
    private final JdbcClient jdbc;
    private final Clock clock;

    JpaReportingPeriodRepository(ReportingPeriodJpaRepository periods, JdbcClient jdbc, Clock clock) {
        this.periods = periods;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<ReportingPeriod> findById(PeriodId id) {
        return viewById(id)
                .map(view -> ReportingPeriod.rehydrate(
                        view.id(),
                        view.code(),
                        view.periodType(),
                        view.startsOn(),
                        view.endsOn(),
                        view.submissionDeadline(),
                        view.status(),
                        view.openedAt(),
                        view.closedAt(),
                        metricScopeOf(id),
                        participantsOf(id),
                        view.version()));
    }

    @Override
    public boolean existsByCode(PeriodCode code) {
        return periods.existsByCode(code.value());
    }

    @Override
    public ReportingPeriod insert(ReportingPeriod period, UserId createdBy) {
        ReportingPeriodEntity entity = new ReportingPeriodEntity(
                period.id().value(),
                period.code().value(),
                period.periodType().name(),
                period.startsOn(),
                period.endsOn(),
                period.submissionDeadline(),
                period.status().name(),
                createdBy == null ? null : createdBy.value(),
                Instant.now(clock));
        try {
            periods.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex, period);
        }
        syncScopes(period);
        return findById(period.id()).orElseThrow();
    }

    @Override
    public ReportingPeriod update(ReportingPeriod period, long expectedVersion, UserId updatedBy) {
        ReportingPeriodEntity entity = periods.findById(period.id().value())
                .orElseThrow(() -> new IllegalStateException("Period disappeared while being updated"));

        if (entity.getVersion() != expectedVersion) {
            throw versionConflict(period.id(), expectedVersion);
        }

        entity.apply(
                period.startsOn(),
                period.endsOn(),
                period.submissionDeadline(),
                period.status().name(),
                period.openedAt().orElse(null),
                period.closedAt().orElse(null),
                updatedBy == null ? null : updatedBy.value(),
                Instant.now(clock));
        try {
            periods.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw versionConflict(period.id(), expectedVersion);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex, period);
        }

        syncScopes(period);
        return findById(period.id()).orElseThrow();
    }

    private ConflictException translate(DataIntegrityViolationException ex, ReportingPeriod period) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("ex_reporting_period_no_overlap")) {
            return new ConflictException(
                    ErrorCode.PERIOD_OVERLAP,
                    "Another period of this type already covers part of these dates",
                    Map.of(
                            "periodCode",
                            period.code().value(),
                            "periodType",
                            period.periodType().name()));
        }
        return new ConflictException(
                ErrorCode.DUPLICATE_RESOURCE,
                "A period with this code already exists",
                Map.of("periodCode", period.code().value()));
    }

    private static ConflictException versionConflict(PeriodId id, long expectedVersion) {
        return new ConflictException(
                ErrorCode.VERSION_CONFLICT,
                "The period was modified by someone else; reload it and try again",
                Map.of("periodId", id.toString(), "expectedVersion", expectedVersion));
    }

    private void syncScopes(ReportingPeriod period) {
        UUID periodId = period.id().value();

        jdbc.sql("DELETE FROM opsflow.reporting_period_metric WHERE reporting_period_id = :id")
                .param("id", periodId)
                .update();
        period.metricScope().forEach(metric -> jdbc.sql(
                        """
                                INSERT INTO opsflow.reporting_period_metric
                                       (reporting_period_id, metric_id, required, display_order)
                                VALUES (:periodId, :metricId, :required, :displayOrder)
                                """)
                .param("periodId", periodId)
                .param("metricId", metric.metricId().value())
                .param("required", metric.required())
                .param("displayOrder", metric.displayOrder())
                .update());

        jdbc.sql("DELETE FROM opsflow.reporting_period_organization WHERE reporting_period_id = :id")
                .param("id", periodId)
                .update();
        period.participants().forEach(organizationId -> jdbc.sql(
                        """
                                INSERT INTO opsflow.reporting_period_organization
                                       (reporting_period_id, organization_id)
                                VALUES (:periodId, :organizationId)
                                """)
                .param("periodId", periodId)
                .param("organizationId", organizationId.value())
                .update());
    }

    private Set<PeriodMetric> metricScopeOf(PeriodId id) {
        return jdbc
                .sql(
                        """
                        SELECT metric_id, required, display_order
                          FROM opsflow.reporting_period_metric
                         WHERE reporting_period_id = :id
                         ORDER BY display_order, metric_id
                        """)
                .param("id", id.value())
                .query((rs, rowNum) -> new PeriodMetric(
                        MetricId.of(rs.getObject("metric_id", UUID.class)),
                        rs.getBoolean("required"),
                        rs.getInt("display_order")))
                .list()
                .stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Optional<PeriodView> viewById(PeriodId id) {
        return jdbc.sql(SELECT_VIEW + " WHERE p.id = :id")
                .param("id", id.value())
                .query(JpaReportingPeriodRepository::toView)
                .optional();
    }

    @Override
    public Optional<PeriodView> viewByCode(PeriodCode code) {
        return jdbc.sql(SELECT_VIEW + " WHERE p.code = :code")
                .param("code", code.value())
                .query(JpaReportingPeriodRepository::toView)
                .optional();
    }

    @Override
    public PeriodPage search(PeriodStatus status, PeriodType type, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (status != null) {
            where.append(" AND p.status = :status");
        }
        if (type != null) {
            where.append(" AND p.period_type = :type");
        }

        var content = jdbc.sql(SELECT_VIEW + where + " ORDER BY p.starts_on DESC LIMIT :limit OFFSET :offset");
        var count = jdbc.sql("SELECT count(*) FROM opsflow.reporting_period p" + where);
        for (var spec : List.of(content, count)) {
            if (status != null) {
                spec.param("status", status.name());
            }
            if (type != null) {
                spec.param("type", type.name());
            }
        }

        List<PeriodView> rows = content.param("limit", size)
                .param("offset", (long) page * size)
                .query(JpaReportingPeriodRepository::toView)
                .list();
        return new PeriodPage(rows, count.query(Long.class).single());
    }

    @Override
    public List<ScopedMetric> scopeOf(PeriodId id) {
        return jdbc.sql(
                        """
                        SELECT m.id, m.code, m.name, m.unit, m.value_type, m.min_value, m.max_value,
                               pm.required, pm.display_order
                          FROM opsflow.reporting_period_metric pm
                          JOIN opsflow.metric_definition m ON m.id = pm.metric_id
                         WHERE pm.reporting_period_id = :id
                         ORDER BY pm.display_order, m.code
                        """)
                .param("id", id.value())
                .query((rs, rowNum) -> new ScopedMetric(
                        MetricId.of(rs.getObject("id", UUID.class)),
                        new MetricCode(rs.getString("code")),
                        rs.getString("name"),
                        rs.getString("unit"),
                        ValueType.valueOf(rs.getString("value_type")),
                        new NumericRange(rs.getBigDecimal("min_value"), rs.getBigDecimal("max_value")),
                        rs.getBoolean("required"),
                        rs.getInt("display_order")))
                .list();
    }

    @Override
    public Set<OrganizationId> participantsOf(PeriodId id) {
        return jdbc
                .sql(
                        """
                        SELECT organization_id FROM opsflow.reporting_period_organization
                         WHERE reporting_period_id = :id ORDER BY organization_id
                        """)
                .param("id", id.value())
                .query((rs, rowNum) -> OrganizationId.of(rs.getObject("organization_id", UUID.class)))
                .list()
                .stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean isParticipant(PeriodId id, OrganizationId organizationId) {
        return jdbc.sql(
                                """
                        SELECT count(*) FROM opsflow.reporting_period_organization
                         WHERE reporting_period_id = :id AND organization_id = :organizationId
                        """)
                        .param("id", id.value())
                        .param("organizationId", organizationId.value())
                        .query(Long.class)
                        .single()
                > 0;
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static PeriodView toView(ResultSet rs, int rowNum) throws SQLException {
        return new PeriodView(
                PeriodId.of(rs.getObject("id", UUID.class)),
                new PeriodCode(rs.getString("code")),
                PeriodType.valueOf(rs.getString("period_type")),
                rs.getObject("starts_on", java.time.LocalDate.class),
                rs.getObject("ends_on", java.time.LocalDate.class),
                rs.getObject("submission_deadline", java.time.LocalDate.class),
                PeriodStatus.valueOf(rs.getString("status")),
                // The PostgreSQL driver will not convert timestamptz straight to Instant.
                toInstant(rs.getObject("opened_at", OffsetDateTime.class)),
                toInstant(rs.getObject("closed_at", OffsetDateTime.class)),
                rs.getInt("metric_count"),
                rs.getInt("participant_count"),
                rs.getLong("version"));
    }
}
