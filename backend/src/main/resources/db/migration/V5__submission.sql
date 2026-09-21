-- V5 — Submissions, their values and their workflow history.

CREATE TABLE opsflow.submission (
    id                  uuid PRIMARY KEY,
    organization_id     uuid NOT NULL REFERENCES opsflow.organization(id)     ON DELETE RESTRICT,
    reporting_period_id uuid NOT NULL REFERENCES opsflow.reporting_period(id) ON DELETE RESTRICT,
    status              text NOT NULL DEFAULT 'DRAFT',

    -- The reviewer currently holding it; cleared on release and on a decision.
    reviewer_id         uuid REFERENCES opsflow.app_user(id),
    submitted_at        timestamptz,
    submitted_by        uuid REFERENCES opsflow.app_user(id),
    decided_at          timestamptz,
    decided_by          uuid REFERENCES opsflow.app_user(id),
    rejection_reason    text,

    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES opsflow.app_user(id),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid REFERENCES opsflow.app_user(id),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_submission_status CHECK (
        status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
    -- A claim is what UNDER_REVIEW means, so the two cannot disagree.
    CONSTRAINT ck_submission_reviewer  CHECK ((status = 'UNDER_REVIEW') = (reviewer_id IS NOT NULL)),
    CONSTRAINT ck_submission_submitted CHECK ((status = 'DRAFT') = (submitted_at IS NULL)),
    CONSTRAINT ck_submission_decided   CHECK (
        (status IN ('APPROVED','REJECTED')) = (decided_at IS NOT NULL)),
    CONSTRAINT ck_submission_rejection CHECK (
        (status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_submission_reason_length CHECK (
        rejection_reason IS NULL OR length(btrim(rejection_reason)) BETWEEN 1 AND 500)
);

-- One submission per organization per period: the rule the whole aggregate rests on,
-- and the only race-free way to enforce it against concurrent creates.
CREATE UNIQUE INDEX ux_submission_org_period
    ON opsflow.submission (organization_id, reporting_period_id);
CREATE INDEX ix_submission_period_status ON opsflow.submission (reporting_period_id, status);
CREATE INDEX ix_submission_organization  ON opsflow.submission (organization_id);
CREATE INDEX ix_submission_review_queue  ON opsflow.submission (status) WHERE status IN ('SUBMITTED','UNDER_REVIEW');


CREATE TABLE opsflow.submission_value (
    id            uuid PRIMARY KEY,
    submission_id uuid NOT NULL REFERENCES opsflow.submission(id)         ON DELETE CASCADE,
    metric_id     uuid NOT NULL REFERENCES opsflow.metric_definition(id)  ON DELETE RESTRICT,
    value         numeric(18,4) NOT NULL,
    note          text,
    -- Whether the number was typed or uploaded. Deliberately not a reference to an
    -- import job: submission is owned below imports, and a foreign key upwards would
    -- point against the module dependency direction (ADR-0001).
    source        text NOT NULL DEFAULT 'MANUAL',

    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid REFERENCES opsflow.app_user(id),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid REFERENCES opsflow.app_user(id),

    CONSTRAINT ux_submission_value        UNIQUE (submission_id, metric_id),
    CONSTRAINT ck_submission_value_source CHECK (source IN ('MANUAL','IMPORT')),
    CONSTRAINT ck_submission_value_note   CHECK (note IS NULL OR length(note) <= 500)
);

CREATE INDEX ix_submission_value_metric ON opsflow.submission_value (metric_id, submission_id);


-- Workflow history: who moved this submission, when, and why. Not a general audit
-- log -- it records status changes only, never value-level before/after.
CREATE TABLE opsflow.submission_transition (
    id            uuid PRIMARY KEY,
    submission_id uuid NOT NULL REFERENCES opsflow.submission(id) ON DELETE CASCADE,
    from_status   text,
    to_status     text NOT NULL,
    actor_id      uuid REFERENCES opsflow.app_user(id),
    reason        text,
    occurred_at   timestamptz NOT NULL,

    CONSTRAINT ck_transition_from CHECK (
        from_status IS NULL
        OR from_status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
    CONSTRAINT ck_transition_to CHECK (
        to_status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
    CONSTRAINT ck_transition_reason CHECK (reason IS NULL OR length(btrim(reason)) BETWEEN 1 AND 500)
);

CREATE INDEX ix_submission_transition_submission
    ON opsflow.submission_transition (submission_id, occurred_at, id);
