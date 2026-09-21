-- V6 — CSV import jobs and their row errors.
--
-- Spring Batch keeps its own execution metadata in a separate `batch` schema so
-- framework tables never mix with business ones.

CREATE SCHEMA IF NOT EXISTS batch;

CREATE TABLE opsflow.import_job (
    id                  uuid PRIMARY KEY,
    reporting_period_id uuid NOT NULL REFERENCES opsflow.reporting_period(id) ON DELETE RESTRICT,
    -- NULL means the file may carry rows for any organization the initiator may write to.
    organization_id     uuid REFERENCES opsflow.organization(id) ON DELETE RESTRICT,
    status              text NOT NULL DEFAULT 'PENDING',

    -- The basis on which this one job was admitted. Not a role: written once from a
    -- verified token, scoped to this job, read only when the job applies values.
    authorization_mode  text NOT NULL,

    idempotency_key     text NOT NULL,
    -- SHA-256 over the whole logical request, not just the file: the same bytes sent
    -- against a different period are a different import.
    request_fingerprint text NOT NULL,

    -- Client-supplied name, kept as data only; it never takes part in building a path.
    original_filename   text NOT NULL,
    storage_ref         text NOT NULL,
    file_size_bytes     bigint NOT NULL,
    file_checksum       text NOT NULL,

    submitted_rows      integer NOT NULL DEFAULT 0,
    processed_rows      integer NOT NULL DEFAULT 0,
    valid_rows          integer NOT NULL DEFAULT 0,
    invalid_rows        integer NOT NULL DEFAULT 0,

    failure_code        text,
    failure_message     text,
    started_at          timestamptz,
    finished_at         timestamptz,

    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid NOT NULL REFERENCES opsflow.app_user(id),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_import_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_import_authorization_mode
        CHECK (authorization_mode IN ('ORGANIZATION_MEMBER','ADMIN_OVERRIDE')),
    CONSTRAINT ck_import_counts CHECK (
        submitted_rows >= 0 AND processed_rows >= 0 AND valid_rows >= 0 AND invalid_rows >= 0),
    CONSTRAINT ck_import_checksum CHECK (file_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_import_finished CHECK ((status IN ('COMPLETED','FAILED')) = (finished_at IS NOT NULL))
);

-- Scoped per user: a global unique key would let one user's arbitrary string block
-- another's, and hand them back a job that is not theirs.
CREATE UNIQUE INDEX ux_import_idempotency ON opsflow.import_job (created_by, idempotency_key);

-- One live import per scope, enforced by the database rather than by a check-then-act.
CREATE UNIQUE INDEX ux_import_active_per_scope
    ON opsflow.import_job (reporting_period_id, coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status IN ('PENDING','RUNNING');

CREATE INDEX ix_import_job_created_by ON opsflow.import_job (created_by, created_at DESC);
CREATE INDEX ix_import_job_period ON opsflow.import_job (reporting_period_id);
CREATE INDEX ix_import_job_status ON opsflow.import_job (status) WHERE status IN ('PENDING','RUNNING');


CREATE TABLE opsflow.import_row_error (
    id            uuid PRIMARY KEY,
    import_job_id uuid NOT NULL REFERENCES opsflow.import_job(id) ON DELETE CASCADE,
    row_number    integer NOT NULL,
    code          text NOT NULL,
    message       text NOT NULL,
    -- A rule that invalidates a whole organization's rows is recorded once, with the
    -- number of rows it cost. Writing one row per affected line would bury the errors
    -- the user can actually act on.
    affected_rows integer NOT NULL DEFAULT 1,
    created_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_import_row_error_row CHECK (row_number >= 0),
    CONSTRAINT ck_import_row_error_affected CHECK (affected_rows >= 1),
    CONSTRAINT ck_import_row_error_message CHECK (length(message) <= 500)
);

CREATE INDEX ix_import_row_error_job ON opsflow.import_row_error (import_job_id, row_number, id);


-- Spring Batch execution metadata, verbatim from spring-batch-core 6.0.5's PostgreSQL
-- schema with every object qualified into the `batch` schema. Kept here rather than
-- created by Boot at startup so Flyway owns every table in this database, framework
-- tables included, and an upgrade that changes them shows up as a migration.
CREATE TABLE batch.BATCH_JOB_INSTANCE (
	JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY,
	VERSION BIGINT,
	JOB_NAME VARCHAR(100) NOT NULL,
	JOB_KEY VARCHAR(32) NOT NULL,
	constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ;

CREATE TABLE batch.BATCH_JOB_EXECUTION (
	JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY,
	VERSION BIGINT,
	JOB_INSTANCE_ID BIGINT NOT NULL,
	CREATE_TIME TIMESTAMP NOT NULL,
	START_TIME TIMESTAMP DEFAULT NULL,
	END_TIME TIMESTAMP DEFAULT NULL,
	STATUS VARCHAR(10),
	EXIT_CODE VARCHAR(2500),
	EXIT_MESSAGE VARCHAR(2500),
	LAST_UPDATED TIMESTAMP,
	constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
	references batch.BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ;

CREATE TABLE batch.BATCH_JOB_EXECUTION_PARAMS (
	JOB_EXECUTION_ID BIGINT NOT NULL,
	PARAMETER_NAME VARCHAR(100) NOT NULL,
	PARAMETER_TYPE VARCHAR(100) NOT NULL,
	PARAMETER_VALUE VARCHAR(2500),
	IDENTIFYING CHAR(1) NOT NULL,
	constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
	references batch.BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;

CREATE TABLE batch.BATCH_STEP_EXECUTION (
	STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY,
	VERSION BIGINT NOT NULL,
	STEP_NAME VARCHAR(100) NOT NULL,
	JOB_EXECUTION_ID BIGINT NOT NULL,
	CREATE_TIME TIMESTAMP NOT NULL,
	START_TIME TIMESTAMP DEFAULT NULL,
	END_TIME TIMESTAMP DEFAULT NULL,
	STATUS VARCHAR(10),
	COMMIT_COUNT BIGINT,
	READ_COUNT BIGINT,
	FILTER_COUNT BIGINT,
	WRITE_COUNT BIGINT,
	READ_SKIP_COUNT BIGINT,
	WRITE_SKIP_COUNT BIGINT,
	PROCESS_SKIP_COUNT BIGINT,
	ROLLBACK_COUNT BIGINT,
	EXIT_CODE VARCHAR(2500),
	EXIT_MESSAGE VARCHAR(2500),
	LAST_UPDATED TIMESTAMP,
	constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
	references batch.BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;

CREATE TABLE batch.BATCH_STEP_EXECUTION_CONTEXT (
	STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT,
	constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
	references batch.BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ;

CREATE TABLE batch.BATCH_JOB_EXECUTION_CONTEXT (
	JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT,
	constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
	references batch.BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;

CREATE SEQUENCE batch.BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE batch.BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE batch.BATCH_JOB_INSTANCE_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
