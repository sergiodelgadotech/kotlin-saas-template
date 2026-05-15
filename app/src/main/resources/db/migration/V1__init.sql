-- V1__init.sql
-- Complete app schema. JobRunr owns its own tables via DatabaseCreator.

CREATE TABLE organizations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    plan       VARCHAR(50)  NOT NULL DEFAULT 'starter',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE members (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    zitadel_user_id VARCHAR(255) NOT NULL UNIQUE,
    role            VARCHAR(50)  NOT NULL DEFAULT 'member',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Critical: this index is hit on every authenticated request
CREATE INDEX idx_members_zitadel_user_id ON members(zitadel_user_id);
CREATE INDEX idx_members_organization_id ON members(organization_id);

CREATE TABLE subscriptions (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID         NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    stripe_customer_id     VARCHAR(255) NOT NULL UNIQUE,
    stripe_subscription_id VARCHAR(255),
    plan                   VARCHAR(50)  NOT NULL DEFAULT 'STARTER',
    status                 VARCHAR(50)  NOT NULL DEFAULT 'TRIALING',
    current_period_end     TIMESTAMPTZ,
    cancel_at_period_end   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_stripe_customer_id     ON subscriptions(stripe_customer_id);
CREATE INDEX idx_subscriptions_stripe_subscription_id ON subscriptions(stripe_subscription_id);

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
