-- V4 — Metric catalogue and reporting periods.

-- Required by the period overlap constraint below: btree_gist lets a GiST index
-- mix an equality column with a range column.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE opsflow.metric_definition (
    id          uuid PRIMARY KEY,
    -- Immutable once assigned: CSV imports reference it.
    code        text NOT NULL,
    name        text NOT NULL,
    description text,
    unit        text NOT NULL,
    value_type  text NOT NULL,
    min_value   numeric(18,4),
    max_value   numeric(18,4),
    active      boolean NOT NULL DEFAULT true,

    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid REFERENCES opsflow.app_user(id),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid REFERENCES opsflow.app_user(id),
    version     bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_metric_code       CHECK (code ~ '^[A-Z0-9_]{2,40}$'),
    CONSTRAINT ck_metric_name       CHECK (length(btrim(name)) BETWEEN 2 AND 120),
    CONSTRAINT ck_metric_unit       CHECK (length(btrim(unit)) BETWEEN 1 AND 32),
    CONSTRAINT ck_metric_value_type CHECK (value_type IN ('INTEGER','DECIMAL','PERCENTAGE')),
    CONSTRAINT ck_metric_range      CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value),
    CONSTRAINT ck_metric_percentage CHECK (
        value_type <> 'PERCENTAGE'
        OR (COALESCE(min_value, 0) >= 0 AND COALESCE(max_value, 100) <= 100))
);

CREATE UNIQUE INDEX ux_metric_definition_code ON opsflow.metric_definition (code);
CREATE INDEX ix_metric_definition_active ON opsflow.metric_definition (active) WHERE active;


CREATE TABLE opsflow.reporting_period (
    id                  uuid PRIMARY KEY,
    code                text NOT NULL,
    period_type         text NOT NULL,
    starts_on           date NOT NULL,
    ends_on             date NOT NULL,
    submission_deadline date NOT NULL,
    status              text NOT NULL DEFAULT 'DRAFT',
    opened_at           timestamptz,
    closed_at           timestamptz,

    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES opsflow.app_user(id),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid REFERENCES opsflow.app_user(id),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_period_code     CHECK (code ~ '^[A-Z0-9-]{4,20}$'),
    CONSTRAINT ck_period_type     CHECK (period_type IN ('MONTH','QUARTER','YEAR')),
    CONSTRAINT ck_period_status   CHECK (status IN ('DRAFT','OPEN','CLOSED')),
    CONSTRAINT ck_period_dates    CHECK (starts_on < ends_on),
    CONSTRAINT ck_period_deadline CHECK (submission_deadline >= ends_on),
    CONSTRAINT ck_period_opened   CHECK (status = 'DRAFT' OR opened_at IS NOT NULL),
    CONSTRAINT ck_period_closed   CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_reporting_period_code ON opsflow.reporting_period (code);
CREATE INDEX ix_reporting_period_status ON opsflow.reporting_period (status);

-- Overlapping periods of the same type would double-count every figure derived
-- from them. Declarative and race-free, which application code cannot be.
ALTER TABLE opsflow.reporting_period
    ADD CONSTRAINT ex_reporting_period_no_overlap
    EXCLUDE USING gist (
        period_type WITH =,
        daterange(starts_on, ends_on, '[]') WITH &&
    );


CREATE TABLE opsflow.reporting_period_metric (
    reporting_period_id uuid NOT NULL REFERENCES opsflow.reporting_period(id)  ON DELETE CASCADE,
    metric_id           uuid NOT NULL REFERENCES opsflow.metric_definition(id) ON DELETE RESTRICT,
    required            boolean NOT NULL DEFAULT true,
    display_order       int NOT NULL DEFAULT 0,
    PRIMARY KEY (reporting_period_id, metric_id)
);

CREATE INDEX ix_period_metric_metric ON opsflow.reporting_period_metric (metric_id);


-- Frozen at DRAFT -> OPEN. Without it, completion figures for a closed period
-- would shift whenever an organization is later created or archived.
CREATE TABLE opsflow.reporting_period_organization (
    reporting_period_id uuid NOT NULL REFERENCES opsflow.reporting_period(id) ON DELETE CASCADE,
    organization_id     uuid NOT NULL REFERENCES opsflow.organization(id)     ON DELETE RESTRICT,
    PRIMARY KEY (reporting_period_id, organization_id)
);

CREATE INDEX ix_period_organization_organization
    ON opsflow.reporting_period_organization (organization_id);
