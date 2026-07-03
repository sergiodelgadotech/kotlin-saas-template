-- V200__app_init.sql
-- App-specific tables. Multi-tenant baseline (organizations, members, subscriptions) lives
-- in kotlin-saas-starter's V100__starter_baseline.sql.

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

CREATE TABLE analysis_results (
    id                         UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id            UUID             NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    run_id                     UUID             NOT NULL,
    client                     VARCHAR(255)     NOT NULL,
    article                    VARCHAR(255)     NOT NULL,
    time_window                VARCHAR(50)      NOT NULL,
    slope                      DOUBLE PRECISION NOT NULL,
    intercept                  DOUBLE PRECISION NOT NULL,
    r_squared                  DOUBLE PRECISION NOT NULL,
    sample_size                INT              NOT NULL,
    has_positive_slope_warning BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_results_org_run  ON analysis_results(organization_id, run_id);
CREATE INDEX idx_analysis_results_warnings ON analysis_results(organization_id, has_positive_slope_warning)
    WHERE has_positive_slope_warning = TRUE;

-- Binary avatar storage. Kept in a separate table (not a members column) so BYTEA
-- blobs do not bloat every member query; Spring Data JDBC would load them eagerly
-- if they were columns on the members row.
--
-- organization_id references organizations (not members) so this table adds no
-- template↔starter coupling; the starter owns V100 which defines organizations.
CREATE TABLE avatar_image (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    external_user_id VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    bytes            BYTEA        NOT NULL,
    source           VARCHAR(20)  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, external_user_id)
);
