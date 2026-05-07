-- V2__jobs.sql
-- Jobrunr storage tables + imports domain table

-- Jobrunr requires these tables to manage background jobs.
-- In production Jobrunr can create them automatically, but
-- explicit Flyway migrations give us version control over schema.

CREATE TABLE jobrunr_jobs (
    id                    UUID         NOT NULL,
    version               INT          NOT NULL,
    jobasjson             TEXT         NOT NULL,
    jobsignature          VARCHAR(512) NOT NULL,
    state                 VARCHAR(36)  NOT NULL,
    createdat             TIMESTAMPTZ  NOT NULL,
    updatedat             TIMESTAMPTZ  NOT NULL,
    scheduledat           TIMESTAMPTZ,
    recurringjobid        VARCHAR(128),
    CONSTRAINT jobrunr_jobs_pkey PRIMARY KEY (id)
);

CREATE INDEX jobrunr_state_idx          ON jobrunr_jobs (state);
CREATE INDEX jobrunr_job_signature_idx  ON jobrunr_jobs (jobsignature);
CREATE INDEX jobrunr_scheduled_idx      ON jobrunr_jobs (scheduledat);
CREATE INDEX jobrunr_recurring_job_idx  ON jobrunr_jobs (recurringjobid);

CREATE TABLE jobrunr_recurring_jobs (
    id              VARCHAR(128) NOT NULL,
    version         INT          NOT NULL,
    jobasjson       TEXT         NOT NULL,
    CONSTRAINT jobrunr_recurring_jobs_pkey PRIMARY KEY (id)
);

CREATE TABLE jobrunr_backgroundjobservers (
    id                  UUID         NOT NULL,
    workerpoolsize      INT          NOT NULL,
    pollintervalinseconds INT        NOT NULL,
    firstheartbeat      TIMESTAMPTZ  NOT NULL,
    lastheartbeat       TIMESTAMPTZ  NOT NULL,
    running             BOOLEAN      NOT NULL,
    systemtotalmemory   BIGINT       NOT NULL,
    systemfreememory    BIGINT       NOT NULL,
    systemcpuload       DECIMAL(3,2) NOT NULL,
    processmaxmemory    BIGINT       NOT NULL,
    processfreememory   BIGINT       NOT NULL,
    processallocatedmemory BIGINT    NOT NULL,
    processcpuload      DECIMAL(3,2) NOT NULL,
    CONSTRAINT jobrunr_backgroundjobservers_pkey PRIMARY KEY (id)
);

CREATE TABLE jobrunr_metadata (
    id          VARCHAR(156) NOT NULL,
    name        VARCHAR(92)  NOT NULL,
    owner       VARCHAR(64)  NOT NULL,
    value       TEXT         NOT NULL,
    createdat   TIMESTAMPTZ  NOT NULL,
    updatedat   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT jobrunr_metadata_pkey PRIMARY KEY (id)
);

-- Domain: file imports
CREATE TABLE imports (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    filename        VARCHAR(512) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    row_count       INT,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_imports_organization_id ON imports(organization_id);
CREATE INDEX idx_imports_status          ON imports(status);
