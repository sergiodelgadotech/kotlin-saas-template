-- Local/test seed: one org + member for the shared test identity (TestAutoAuthFilter.TEST_USER_ID).
-- Applied when db/migration/local is in spring.flyway.locations; never runs in prod.

INSERT INTO organizations (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Dev Org', 'dev-org');

INSERT INTO members (organization_id, external_user_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'local-dev-user', 'OWNER');

INSERT INTO members (organization_id, external_user_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'member-user', 'MEMBER');
