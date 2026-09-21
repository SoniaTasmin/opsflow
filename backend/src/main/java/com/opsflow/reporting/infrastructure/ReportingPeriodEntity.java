/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reporting_period", schema = "opsflow")
public class ReportingPeriodEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "period_type", nullable = false, updatable = false)
    private String periodType;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "submission_deadline", nullable = false)
    private LocalDate submissionDeadline;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReportingPeriodEntity() {}

    ReportingPeriodEntity(
            UUID id,
            String code,
            String periodType,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate submissionDeadline,
            String status,
            UUID createdBy,
            Instant now) {
        this.id = id;
        this.code = code;
        this.periodType = periodType;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.submissionDeadline = submissionDeadline;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.updatedAt = now;
    }

    void apply(
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate submissionDeadline,
            String status,
            Instant openedAt,
            Instant closedAt,
            UUID updatedBy,
            Instant now) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.submissionDeadline = submissionDeadline;
        this.status = status;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    long getVersion() {
        return version;
    }
}
