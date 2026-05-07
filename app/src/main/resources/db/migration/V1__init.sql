-- V1__init.sql
-- Core schema for multi-tenant SaaS

CREATE TABLE organizations (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    plan       VARCHAR(50)  NOT NULL DEFAULT 'starter',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE members (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    zitadel_user_id   VARCHAR(255) NOT NULL UNIQUE,
    role            VARCHAR(50)  NOT NULL DEFAULT 'member',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Critical: this index is hit on every authenticated request
CREATE INDEX idx_members_zitadel_user_id ON members(zitadel_user_id);
CREATE INDEX idx_members_organization_id ON members(organization_id);

CREATE TABLE subscriptions (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID        NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    stripe_customer_id     VARCHAR(255) NOT NULL UNIQUE,
    stripe_subscription_id VARCHAR(255),
    plan                   VARCHAR(50)  NOT NULL DEFAULT 'STARTER',
    status                 VARCHAR(50)  NOT NULL DEFAULT 'TRIALING',
    current_period_end     TIMESTAMPTZ,
    cancel_at_period_end   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_stripe_customer_id ON subscriptions(stripe_customer_id);
CREATE INDEX idx_subscriptions_stripe_subscription_id ON subscriptions(stripe_subscription_id);
