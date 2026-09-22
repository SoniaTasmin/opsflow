/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.infrastructure;

import com.opsflow.identity.UserId;
import com.opsflow.organization.OrganizationId;
import com.opsflow.platform.domain.UuidV7;
import com.opsflow.platform.error.ConflictException;
import com.opsflow.platform.error.ErrorCode;
import com.opsflow.reporting.MetricId;
import com.opsflow.reporting.PeriodId;
import com.opsflow.submission.SubmissionId;
import com.opsflow.submission.SubmissionStatus;
import com.opsflow.submission.ValueSource;
import com.opsflow.submission.domain.StatusTransition;
import com.opsflow.submission.domain.Submission;
import com.opsflow.submission.domain.SubmissionRepository;
import com.opsflow.submission.domain.SubmissionSearchCriteria;
import com.opsflow.submission.domain.SubmissionValue;
import com.opsflow.submission.domain.SubmissionView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
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
class JpaSubmissionRepository implements SubmissionRepository {

    private static final String SELECT_VIEW =
            """
            SELECT s.id, s.organization_id, s.reporting_period_id, s.status, s.reviewer_id,
                   s.submitted_at, s.submitted_by, s.decided_at, s.decided_by, s.rejection_reason,
                   s.updated_at, s.version,
                   (SELECT count(*) FROM opsflow.submission_value v
                     WHERE v.submission_id = s.id) AS value_count
              FROM opsflow.submission s
            """;

    private final SubmissionJpaRepository submissions;
    private final JdbcClient jdbc;
    private final Clock clock;

    JpaSubmissionRepository(SubmissionJpaRepository submissions, JdbcClient jdbc, Clock clock) {
        this.submissions = submissions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Submission> findById(SubmissionId id) {
        return viewById(id)
                .map(view -> Submission.rehydrate(
                        view.id(),
                        view.organizationId(),
                        view.reportingPeriodId(),
                        view.status(),
                        view.reviewerId(),
                        view.submittedAt(),
                        view.submittedBy(),
                        view.decidedAt(),
                        view.decidedBy(),
                        view.rejectionReason(),
                        valuesOf(id),
                        view.version()));
    }

    @Override
    public Optional<SubmissionView> viewById(SubmissionId id) {
        return jdbc.sql(SELECT_VIEW + " WHERE s.id = :id")
                .param("id", id.value())
                .query(JpaSubmissionRepository::toView)
                .optional();
    }

    @Override
    public Optional<SubmissionView> viewByOrganizationAndPeriod(
            OrganizationId organizationId, PeriodId reportingPeriodId) {
        return jdbc.sql(SELECT_VIEW + " WHERE s.organization_id = :organizationId"
                        + " AND s.reporting_period_id = :periodId")
                .param("organizationId", organizationId.value())
                .param("periodId", reportingPeriodId.value())
                .query(JpaSubmissionRepository::toView)
                .optional();
    }

    @Override
    public Optional<Submission> findByOrganizationAndPeriod(OrganizationId organizationId, PeriodId reportingPeriodId) {
        return viewByOrganizationAndPeriod(organizationId, reportingPeriodId).flatMap(view -> findById(view.id()));
    }

    @Override
    public Submission insert(Submission submission, UserId createdBy) {
        SubmissionEntity entity = new SubmissionEntity(
                submission.id().value(),
                submission.organizationId().value(),
                submission.reportingPeriodId().value(),
                submission.status().name(),
                createdBy == null ? null : createdBy.value(),
                Instant.now(clock));
        try {
            submissions.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "This organization already has a submission for this period",
                    Map.of(
                            "organizationId", submission.organizationId().toString(),
                            "reportingPeriodId", submission.reportingPeriodId().toString()));
        }
        syncValues(submission, createdBy);
        appendTransitions(submission);
        return findById(submission.id()).orElseThrow();
    }

    /**
     * Values are part of the aggregate, so a value-only edit must still move the version --
     * otherwise two authors could overwrite each other's numbers without either seeing a
     * conflict. Writing {@code updated_at} on every update is what makes the root row dirty and
     * the version bump unconditional; {@code SubmissionApiIntegrationTest} pins that behaviour.
     */
    @Override
    public Submission update(Submission submission, long expectedVersion, UserId updatedBy) {
        SubmissionEntity entity = submissions
                .findById(submission.id().value())
                .orElseThrow(() -> new IllegalStateException("Submission disappeared while being updated"));

        if (entity.getVersion() != expectedVersion) {
            throw versionConflict(submission.id(), expectedVersion);
        }

        entity.apply(
                submission.status().name(),
                submission.reviewerId().map(UserId::value).orElse(null),
                submission.submittedAt().orElse(null),
                submission.submittedBy().map(UserId::value).orElse(null),
                submission.decidedAt().orElse(null),
                submission.decidedBy().map(UserId::value).orElse(null),
                submission.rejectionReason().orElse(null),
                updatedBy == null ? null : updatedBy.value(),
                Instant.now(clock));
        try {
            submissions.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw versionConflict(submission.id(), expectedVersion);
        }

        syncValues(submission, updatedBy);
        appendTransitions(submission);
        return findById(submission.id()).orElseThrow();
    }

    @Override
    public SubmissionPage search(SubmissionSearchCriteria criteria) {
        List<UUID> visible = criteria.visibleOrganizations() == null
                ? null
                : criteria.visibleOrganizations().stream()
                        .map(OrganizationId::value)
                        .toList();
        if (visible != null && visible.isEmpty()) {
            return new SubmissionPage(List.of(), 0L);
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (visible != null) {
            where.append(" AND s.organization_id = ANY (:visible)");
        }
        if (criteria.organizationId() != null) {
            where.append(" AND s.organization_id = :organizationId");
        }
        if (criteria.reportingPeriodId() != null) {
            where.append(" AND s.reporting_period_id = :periodId");
        }
        if (criteria.status() != null) {
            where.append(" AND s.status = :status");
        }

        var content = jdbc.sql(SELECT_VIEW + where + " ORDER BY s.updated_at DESC, s.id LIMIT :limit OFFSET :offset");
        var count = jdbc.sql("SELECT count(*) FROM opsflow.submission s" + where);
        for (var spec : List.of(content, count)) {
            if (visible != null) {
                spec.param("visible", visible.toArray(UUID[]::new));
            }
            if (criteria.organizationId() != null) {
                spec.param("organizationId", criteria.organizationId().value());
            }
            if (criteria.reportingPeriodId() != null) {
                spec.param("periodId", criteria.reportingPeriodId().value());
            }
            if (criteria.status() != null) {
                spec.param("status", criteria.status().name());
            }
        }

        List<SubmissionView> rows = content.param("limit", criteria.size())
                .param("offset", (long) criteria.page() * criteria.size())
                .query(JpaSubmissionRepository::toView)
                .list();
        return new SubmissionPage(rows, count.query(Long.class).single());
    }

    @Override
    public List<SubmissionValue> valuesOf(SubmissionId id) {
        return jdbc.sql(
                        """
                        SELECT metric_id, value, note, source
                          FROM opsflow.submission_value
                         WHERE submission_id = :id
                         ORDER BY metric_id
                        """)
                .param("id", id.value())
                .query((rs, rowNum) -> new SubmissionValue(
                        MetricId.of(rs.getObject("metric_id", UUID.class)),
                        rs.getBigDecimal("value"),
                        rs.getString("note"),
                        ValueSource.valueOf(rs.getString("source"))))
                .list();
    }

    @Override
    public List<StatusTransition> historyOf(SubmissionId id) {
        return jdbc.sql(
                        """
                        SELECT from_status, to_status, actor_id, reason, occurred_at
                          FROM opsflow.submission_transition
                         WHERE submission_id = :id
                         ORDER BY occurred_at, id
                        """)
                .param("id", id.value())
                .query((rs, rowNum) -> new StatusTransition(
                        status(rs.getString("from_status")),
                        SubmissionStatus.valueOf(rs.getString("to_status")),
                        userId(rs.getObject("actor_id", UUID.class)),
                        rs.getString("reason"),
                        toInstant(rs.getObject("occurred_at", OffsetDateTime.class))))
                .list();
    }

    /**
     * One grouped query for the whole set: the caller's denominator can be hundreds of
     * organizations, and asking per organization is how a summary screen becomes a load test.
     */
    @Override
    public Map<SubmissionStatus, Integer> countByStatus(
            PeriodId reportingPeriodId, Set<OrganizationId> organizationIds) {
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        UUID[] organizations =
                organizationIds.stream().map(OrganizationId::value).toArray(UUID[]::new);

        Map<SubmissionStatus, Integer> counts = new EnumMap<>(SubmissionStatus.class);
        jdbc.sql(
                        """
                SELECT status, count(*) AS total
                  FROM opsflow.submission
                 WHERE reporting_period_id = :periodId
                   AND organization_id = ANY (:organizations)
                 GROUP BY status
                """)
                .param("periodId", reportingPeriodId.value())
                .param("organizations", organizations)
                .query((rs, rowNum) -> counts.put(SubmissionStatus.valueOf(rs.getString("status")), rs.getInt("total")))
                .list();
        return counts;
    }

    private void syncValues(Submission submission, UserId actor) {
        UUID submissionId = submission.id().value();
        List<SubmissionValue> values = submission.values();
        UUID[] keptMetricIds =
                values.stream().map(value -> value.metricId().value()).toArray(UUID[]::new);

        jdbc.sql(
                        """
                        DELETE FROM opsflow.submission_value
                         WHERE submission_id = :submissionId
                           AND NOT (metric_id = ANY (:kept))
                        """)
                .param("submissionId", submissionId)
                .param("kept", keptMetricIds)
                .update();

        Instant now = Instant.now(clock);
        // Upsert rather than delete-and-reinsert so created_at/created_by keep recording
        // when the figure first appeared, not when it was last touched.
        values.forEach(value -> jdbc.sql(
                        """
                        INSERT INTO opsflow.submission_value
                               (id, submission_id, metric_id, value, note, source,
                                created_by, updated_by, updated_at)
                        VALUES (:id, :submissionId, :metricId, :value, :note, :source,
                                :actor, :actor, :now)
                        ON CONFLICT (submission_id, metric_id) DO UPDATE
                            SET value = EXCLUDED.value,
                                note = EXCLUDED.note,
                                source = EXCLUDED.source,
                                updated_by = EXCLUDED.updated_by,
                                updated_at = EXCLUDED.updated_at
                        """)
                .param("id", UuidV7.generate(now))
                .param("submissionId", submissionId)
                .param("metricId", value.metricId().value())
                .param("value", value.value())
                .param("note", value.note())
                .param("source", value.source().name())
                .param("actor", actor == null ? null : actor.value())
                .param("now", OffsetDateTime.ofInstant(now, clock.getZone()))
                .update());
    }

    private void appendTransitions(Submission submission) {
        submission.pendingTransitions().forEach(transition -> jdbc.sql(
                        """
                        INSERT INTO opsflow.submission_transition
                               (id, submission_id, from_status, to_status, actor_id, reason, occurred_at)
                        VALUES (:id, :submissionId, :fromStatus, :toStatus, :actorId, :reason, :occurredAt)
                        """)
                .param("id", UuidV7.generate(transition.occurredAt()))
                .param("submissionId", submission.id().value())
                .param(
                        "fromStatus",
                        transition.from() == null ? null : transition.from().name())
                .param("toStatus", transition.to().name())
                .param(
                        "actorId",
                        transition.actorId() == null
                                ? null
                                : transition.actorId().value())
                .param("reason", transition.reason())
                .param("occurredAt", OffsetDateTime.ofInstant(transition.occurredAt(), clock.getZone()))
                .update());
    }

    private static ConflictException versionConflict(SubmissionId id, long expectedVersion) {
        return new ConflictException(
                ErrorCode.VERSION_CONFLICT,
                "The submission was modified by someone else; reload it and try again",
                Map.of("submissionId", id.toString(), "expectedVersion", expectedVersion));
    }

    private static SubmissionStatus status(String value) {
        return value == null ? null : SubmissionStatus.valueOf(value);
    }

    private static UserId userId(UUID value) {
        return value == null ? null : UserId.of(value);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static SubmissionView toView(ResultSet rs, int rowNum) throws SQLException {
        return new SubmissionView(
                SubmissionId.of(rs.getObject("id", UUID.class)),
                OrganizationId.of(rs.getObject("organization_id", UUID.class)),
                PeriodId.of(rs.getObject("reporting_period_id", UUID.class)),
                SubmissionStatus.valueOf(rs.getString("status")),
                userId(rs.getObject("reviewer_id", UUID.class)),
                toInstant(rs.getObject("submitted_at", OffsetDateTime.class)),
                userId(rs.getObject("submitted_by", UUID.class)),
                toInstant(rs.getObject("decided_at", OffsetDateTime.class)),
                userId(rs.getObject("decided_by", UUID.class)),
                rs.getString("rejection_reason"),
                rs.getInt("value_count"),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)),
                rs.getLong("version"));
    }
}
