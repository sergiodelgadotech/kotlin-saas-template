# JobRunr Schema Ownership Fix

**Date:** 2026-05-15
**Issue:** #12 — TenantIsolationTest fails: JobRunr migration v001 conflicts with Flyway-created jobrunr_jobs table

## Problem

Two systems compete to own the JobRunr schema:

- `V2__jobs.sql` (Flyway) creates `jobrunr_jobs` and related tables.
- JobRunr's `DatabaseCreator` runs its own bundled migrations on startup and fails with `relation "jobrunr_jobs" already exists`.

Compounding this: `V2__jobs.sql` is incomplete for JobRunr 7.3.1. It is missing columns (`deleteSucceededJobsAfter`, `permanentlyDeleteJobsAfter`, `createdAt`, `name`), all stats views, and the JobRunr migrations-tracking table. Even setting `skip-create=true` would leave the app running against a broken schema.

Current workaround in `TenantIsolationTest`: all JobRunr autoconfigurations are excluded and `JobScheduler` is mocked — a hack that prevents real wiring from being tested.

## Decision

**JobRunr owns its schema; Flyway owns app schema.**

JobRunr ships 15+ bundled migrations that it manages correctly across upgrades. Maintaining a hand-rolled copy of that schema in Flyway is a maintenance burden with no upside.

## Changes

### Flyway migrations — squash to a single V1

Replace `V1__init.sql`, `V2__jobs.sql`, and `V3__analysis.sql` with a single `V1__init.sql` containing all app tables (organizations, members, billing, imports, analysis). No `jobrunr_*` DDL.

No DB data to preserve — squashing is safe.

### application.yml — no change needed

JobRunr's `DatabaseCreator` runs by default. No `skip-create` property needed.

### application-test.yml — disable workers and dashboard

```yaml
jobrunr:
  background-job-server:
    enabled: false
  dashboard:
    enabled: false
```

The background job server and dashboard are not needed in tests and would spin up threads unnecessarily.

### TenantIsolationTest — remove workaround

- Remove `spring.autoconfigure.exclude` from `@SpringBootTest`.
- Remove `@MockBean JobScheduler`.
- Remove associated comments.

Result: the test boots a full real Spring context. JobRunr initializes its own schema on the fresh Testcontainers Postgres. The real `JobScheduler` bean satisfies `ImportService` → `JobSchedulerService`. No background workers or dashboard run.

## Out of scope

- Changing how the starter's `JobSchedulerService` is configured.
- Any changes to the `imports` or `analysis` domain logic.
