-- =============================================================================
-- V2 — Identity
--
-- A local projection of the people the identity provider authenticates. It
-- exists so work can be attributed to a stable identifier and names rendered
-- without calling Keycloak per row.
--
-- Two things this table deliberately does NOT hold:
--   * roles      — read from the token on every request. A stored copy is a
--                  privilege-escalation bug with a delay fuse.
--   * credentials — Keycloak owns authentication; nothing here authenticates.
-- =============================================================================

CREATE TABLE opsflow.app_user (
    id            uuid PRIMARY KEY,

    -- How the identity provider identifies this person. varchar(255), not uuid:
    -- OIDC permits any string subject, and assuming Keycloak's happens to be a
    -- UUID is exactly the coupling this table exists to avoid.
    oidc_issuer   varchar(255) NOT NULL,
    oidc_subject  varchar(255) NOT NULL,

    -- Upstream display data. It can change at any time, so nothing keys on it.
    username      varchar(255) NOT NULL,
    display_name  varchar(255) NOT NULL,
    email         varchar(320),          -- nullable: not every provider releases it

    first_seen_at timestamptz  NOT NULL,
    last_seen_at  timestamptz  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT ck_app_user_username     CHECK (length(btrim(username)) > 0),
    CONSTRAINT ck_app_user_display_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_app_user_seen_order   CHECK (last_seen_at >= first_seen_at)
);

-- The identity of a person, and the race guard for concurrent first logins:
-- two simultaneous first-time requests both insert, and exactly one wins. No
-- amount of checking before the insert can close that window; the constraint can.
--
-- It is also read on every authenticated request to resolve the token subject to
-- an application user id, so it must be an index-only lookup.
CREATE UNIQUE INDEX ux_app_user_oidc ON opsflow.app_user (oidc_issuer, oidc_subject);

-- Supports the administrator's member search (M3). Deliberately not unique:
-- usernames are upstream data that can change, and two federated providers could
-- legitimately issue the same one.
CREATE INDEX ix_app_user_username ON opsflow.app_user (lower(username));

COMMENT ON TABLE opsflow.app_user IS
    'Local projection of identity-provider users. Holds no roles and no credentials.';
