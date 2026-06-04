-- Local-only seed: one org + member mapped to the LocalDevAuthFilter identity.
-- This file is only applied when db/migration/local is in spring.flyway.locations
-- (set in application-local.yml), so it never runs in test or prod.

INSERT INTO organizations (id, name, slug, plan)
VALUES ('00000000-0000-0000-0000-000000000001', 'Dev Org', 'dev-org', 'pro');

INSERT INTO members (organization_id, external_user_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'local-dev-user', 'OWNER');
