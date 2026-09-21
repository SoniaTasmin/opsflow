/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.NumericRange;
import com.opsflow.reporting.ValueType;
import com.opsflow.reporting.domain.MetricDefinition;
import com.opsflow.reporting.domain.MetricRepository;
import com.opsflow.reporting.domain.MetricView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaMetricRepository implements MetricRepository {

    private static final String SELECT_VIEW =
            """
            SELECT m.id, m.code, m.name, m.description, m.unit, m.value_type,
                   m.min_value, m.max_value, m.active, m.version
              FROM opsflow.metric_definition m
            """;

    private final MetricDefinitionJpaRepository metrics;
    private final JdbcClient jdbc;
    private final Clock clock;

    JpaMetricRepository(MetricDefinitionJpaRepository metrics, JdbcClient jdbc, Clock clock) {
        this.metrics = metrics;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<MetricDefinition> findById(MetricId id) {
        return viewById(id).map(JpaMetricRepository::toDomain);
    }

    @Override
    public boolean existsByCode(MetricCode code) {
        return metrics.existsByCode(code.value());
    }

    @Override
    public MetricDefinition insert(MetricDefinition metric, UserId createdBy) {
        MetricDefinitionEntity entity = new MetricDefinitionEntity(
                metric.id().value(),
                metric.code().value(),
                metric.name(),
                metric.description().orElse(null),
                metric.unit(),
                metric.valueType().name(),
                metric.range().min(),
                metric.range().max(),
                metric.isActive(),
                createdBy == null ? null : createdBy.value(),
                Instant.now(clock));
        try {
            metrics.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            // Check-then-insert cannot close the race; the unique index decides.
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "A metric with this code already exists",
                    Map.of("metricCode", metric.code().value()));
        }
        return findById(metric.id()).orElseThrow();
    }

    @Override
    public MetricDefinition update(MetricDefinition metric, long expectedVersion, UserId updatedBy) {
        MetricDefinitionEntity entity = metrics.findById(metric.id().value())
                .orElseThrow(() -> new IllegalStateException("Metric disappeared while being updated"));

        if (entity.getVersion() != expectedVersion) {
            throw versionConflict(metric.id(), expectedVersion);
        }

        entity.apply(
                metric.name(),
                metric.description().orElse(null),
                metric.unit(),
                metric.range().min(),
                metric.range().max(),
                metric.isActive(),
                updatedBy == null ? null : updatedBy.value(),
                Instant.now(clock));
        try {
            metrics.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw versionConflict(metric.id(), expectedVersion);
        }
        return findById(metric.id()).orElseThrow();
    }

    private static ConflictException versionConflict(MetricId id, long expectedVersion) {
        return new ConflictException(
                ErrorCode.VERSION_CONFLICT,
                "The metric was modified by someone else; reload it and try again",
                Map.of("metricId", id.toString(), "expectedVersion", expectedVersion));
    }

    @Override
    public Optional<MetricView> viewById(MetricId id) {
        return jdbc.sql(SELECT_VIEW + " WHERE m.id = :id")
                .param("id", id.value())
                .query(JpaMetricRepository::toView)
                .optional();
    }

    @Override
    public Optional<MetricView> viewByCode(MetricCode code) {
        return jdbc.sql(SELECT_VIEW + " WHERE m.code = :code")
                .param("code", code.value())
                .query(JpaMetricRepository::toView)
                .optional();
    }

    @Override
    public List<MetricView> viewsByCode(Set<MetricCode> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT_VIEW + " WHERE m.code IN (:codes)")
                .param("codes", codes.stream().map(MetricCode::value).toList())
                .query(JpaMetricRepository::toView)
                .list();
    }

    @Override
    public List<MetricView> viewsById(Set<MetricId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT_VIEW + " WHERE m.id IN (:ids)")
                .param("ids", ids.stream().map(MetricId::value).toList())
                .query(JpaMetricRepository::toView)
                .list();
    }

    @Override
    public MetricPage search(String term, Boolean active, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (active != null) {
            where.append(" AND m.active = :active");
        }
        boolean hasTerm = term != null && !term.isBlank();
        if (hasTerm) {
            where.append(" AND (lower(m.name) LIKE :term OR lower(m.code) LIKE :term)");
        }

        var content = jdbc.sql(SELECT_VIEW + where + " ORDER BY m.code LIMIT :limit OFFSET :offset");
        var count = jdbc.sql("SELECT count(*) FROM opsflow.metric_definition m" + where);
        for (var spec : List.of(content, count)) {
            if (active != null) {
                spec.param("active", active);
            }
            if (hasTerm) {
                spec.param("term", "%" + term.toLowerCase() + "%");
            }
        }

        List<MetricView> rows = content.param("limit", size)
                .param("offset", (long) page * size)
                .query(JpaMetricRepository::toView)
                .list();
        return new MetricPage(rows, count.query(Long.class).single());
    }

    @Override
    public boolean isUsedByNonDraftPeriod(MetricId id) {
        return jdbc.sql(
                                """
                        SELECT count(*) FROM opsflow.reporting_period_metric pm
                          JOIN opsflow.reporting_period p ON p.id = pm.reporting_period_id
                         WHERE pm.metric_id = :id AND p.status <> 'DRAFT'
                        """)
                        .param("id", id.value())
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    public boolean isUsedByOpenPeriod(MetricId id) {
        return jdbc.sql(
                                """
                        SELECT count(*) FROM opsflow.reporting_period_metric pm
                          JOIN opsflow.reporting_period p ON p.id = pm.reporting_period_id
                         WHERE pm.metric_id = :id AND p.status = 'OPEN'
                        """)
                        .param("id", id.value())
                        .query(Long.class)
                        .single()
                > 0;
    }

    private static MetricDefinition toDomain(MetricView view) {
        return MetricDefinition.rehydrate(
                view.id(),
                view.code(),
                view.name(),
                view.description(),
                view.unit(),
                view.valueType(),
                view.range(),
                view.active(),
                view.version());
    }

    private static MetricView toView(ResultSet rs, int rowNum) throws SQLException {
        return new MetricView(
                MetricId.of(rs.getObject("id", UUID.class)),
                new MetricCode(rs.getString("code")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("unit"),
                ValueType.valueOf(rs.getString("value_type")),
                new NumericRange(rs.getBigDecimal("min_value"), rs.getBigDecimal("max_value")),
                rs.getBoolean("active"),
                rs.getLong("version"));
    }
}
