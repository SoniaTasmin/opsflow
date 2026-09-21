-- V3 — Organizations and membership.
-- Membership carries no role: roles come from the identity provider (ADR-0003).

CREATE TABLE opsflow.organization (
    id          uuid PRIMARY KEY,

    -- Immutable once assigned: CSV imports reference it.
    code        text        NOT NULL,
    name        text        NOT NULL,

    status      text        NOT NULL DEFAULT 'ACTIVE',
    archived_at timestamptz,

    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid REFERENCES opsflow.app_user(id),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid REFERENCES opsflow.app_user(id),
    version     bigint      NOT NULL DEFAULT 0,

    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_organization_code   CHECK (code ~ '^[A-Z0-9_-]{2,32}$'),
    CONSTRAINT ck_organization_name   CHECK (length(btrim(name)) BETWEEN 2 AND 120),

    CONSTRAINT ck_organization_archived CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);

-- Enforced here, not only in code: check-then-insert cannot close the race.
CREATE UNIQUE INDEX ux_organization_code ON opsflow.organization (code);

CREATE INDEX ix_organization_status ON opsflow.organization (status);


CREATE TABLE opsflow.org_membership (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES opsflow.app_user(id)     ON DELETE CASCADE,

    -- RESTRICT, not CASCADE: organizations are archived, never deleted.
    organization_id uuid NOT NULL REFERENCES opsflow.organization(id) ON DELETE RESTRICT,

    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES opsflow.app_user(id),

    CONSTRAINT ux_org_membership UNIQUE (user_id, organization_id)
);

-- Read on every organization-scoped request.
CREATE INDEX ix_org_membership_user ON opsflow.org_membership (user_id);

CREATE INDEX ix_org_membership_org ON opsflow.org_membership (organization_id);

COMMENT ON TABLE opsflow.org_membership IS
    'Associates users with organizations. Carries no role: roles come from the identity provider.';
