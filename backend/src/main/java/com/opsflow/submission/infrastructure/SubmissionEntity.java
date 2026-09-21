/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.submission.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission", schema = "opsflow")
public class SubmissionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "reporting_period_id", nullable = false, updatable = false)
    private UUID reportingPeriodId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SubmissionEntity() {}

    SubmissionEntity(UUID id, UUID organizationId, UUID reportingPeriodId, String status, UUID createdBy, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.reportingPeriodId = reportingPeriodId;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.updatedAt = now;
    }

    void apply(
            String status,
            UUID reviewerId,
            Instant submittedAt,
            UUID submittedBy,
            Instant decidedAt,
            UUID decidedBy,
            String rejectionReason,
            UUID updatedBy,
            Instant now) {
        this.status = status;
        this.reviewerId = reviewerId;
        this.submittedAt = submittedAt;
        this.submittedBy = submittedBy;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.rejectionReason = rejectionReason;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    long getVersion() {
        return version;
    }
}
