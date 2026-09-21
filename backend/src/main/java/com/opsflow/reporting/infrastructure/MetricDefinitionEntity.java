/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.reporting.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metric_definition", schema = "opsflow")
public class MetricDefinitionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "value_type", nullable = false, updatable = false)
    private String valueType;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MetricDefinitionEntity() {}

    MetricDefinitionEntity(
            UUID id,
            String code,
            String name,
            String description,
            String unit,
            String valueType,
            BigDecimal minValue,
            BigDecimal maxValue,
            boolean active,
            UUID createdBy,
            Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.valueType = valueType;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.active = active;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.updatedAt = now;
    }

    void apply(
            String name,
            String description,
            String unit,
            BigDecimal minValue,
            BigDecimal maxValue,
            boolean active,
            UUID updatedBy,
            Instant now) {
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.active = active;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    long getVersion() {
        return version;
    }
}
