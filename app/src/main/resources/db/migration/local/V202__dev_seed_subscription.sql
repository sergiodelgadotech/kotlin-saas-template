-- Dev seed: an ACTIVE STARTER subscription for the local-dev org.
-- Separate from V99 so existing databases can get this row without a full reset.

INSERT INTO subscriptions (id, organization_id, external_customer_id, plan, status)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    NULL,
    'STARTER',
    'ACTIVE'
);
