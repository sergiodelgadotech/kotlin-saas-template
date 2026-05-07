# Starter / Template Split — Design

**Date:** 2026-05-07
**Status:** Approved
**Repos affected:** `kotlin-saas-starter`, `mvp-saas-template` (to be renamed `kotlin-saas-template`)

## Goal

Migrate generic infrastructure from the template into the starter library so that forks of the template inherit improvements via starter version bumps rather than being frozen at fork time. The current split has too much in the template — config classes, multi-tenant entities, and the billing module are all generic enough to live in the starter, but currently sit in the template where forks won't benefit from updates.

## Success criteria

After all 8 plans land:

1. The template's `config/` package contains only `SecurityConfig` (route-specific) and `MemberTenantResolver` (concrete external-auth wiring).
2. The starter ships its own `db/migration/saasstarter/` folder for `organizations`, `members`, and `subscriptions` tables. The template only owns app-domain migrations (jobs, imports, analysis).
3. The starter has Spring Boot autoconfiguration for sessions, JobRunr, Redis defaults, and interceptor wiring. Templates get all four "for free" from the dependency, with the option to override any bean.
4. `Organization`, `Member`, `OrganizationService`, `OrganizationRepository`, `OrganizationValidations` live in the starter.
5. `Subscription`, `SubscriptionRepository`, `BillingService`, `StripeWebhookHandler` live in the starter.
6. At every step, `./gradlew :app:test :app:integrationTest` passes in the template against a corresponding starter version.
7. The `Member.zitadelUserId` and `Subscription.stripe*Id` misnomers are replaced with provider-agnostic `external*Id` names.
8. The template repo is renamed to `kotlin-saas-template` and the Kotlin package to `org.granchi.saastemplate`.

## Out of scope

- `BillingController` and `OrganizationController` stay in the template (route URLs and response templates are app concerns).
- `SecurityConfig` stays in the template (route-specific authorization is per-app).
- No new features. Pure relocation + light parameterization.
- The Zitadel/Clerk decision is tracked separately. The migrations use provider-agnostic `externalUserId`, so this decision is orthogonal.

## Architectural decisions

These are the load-bearing decisions every plan references.

### 1. Hybrid autoconfiguration

The starter uses `@AutoConfiguration` for cross-cutting infrastructure (sessions, JobRunr, Redis defaults, exception handling, interceptor wiring). Multi-tenant entities, services, and billing components are wired manually so apps can compose or replace them. We may escalate to full autoconfig later if the manual wiring proves redundant across forks.

### 2. Customization via bean override

Apps that want different behavior define their own `@Bean` of the same type. The starter's `@ConditionalOnMissingBean` autoconfig backs off. Standard Spring Boot pattern.

### 3. Provider-agnostic external IDs

- `Member.externalUserId` (not `zitadelUserId`, not `clerkUserId`)
- `Subscription.externalCustomerId` (not `stripeCustomerId`)
- `Subscription.externalSubscriptionId` (not `stripeSubscriptionId`)

Apps map their provider's identifier to these fields via `MemberTenantResolver` and `BillingService` configuration.

### 4. Extensibility via small interfaces with default implementations

- `interface MemberRole` + `DefaultMemberRole` enum (`OWNER`, `ADMIN`, `MEMBER`)
- `interface BillingPlan` + `DefaultBillingPlan` enum (`STARTER`, `PRO`, `ENTERPRISE`)

Apps replace with custom enums when the defaults don't fit.

### 5. Starter-owned Flyway migrations

The starter ships migrations under `db/migration/saasstarter/`. The template configures Flyway with two locations: starter's path and the app's `db/migration/`. Schema improvements to starter-owned tables propagate via version bumps.

**Baseline handling:** The template's existing `V1__init.sql` already creates `organizations`, `members`, and `subscriptions`. To avoid conflicts with any installation that already ran V1, the starter's baseline migration uses a distinct version prefix (e.g., `V100__starter_baseline.sql`) and Flyway is configured with `baseline-on-migrate=true` plus a `baseline-version` cutoff. Because this template currently has no production deployments, in practice we will:

1. Edit the template's `V1__init.sql` to remove the `organizations`, `members`, `subscriptions` CREATE TABLEs (these move to starter's `V100__starter_baseline.sql`).
2. Reset local dev databases (`docker compose down -v`) — acceptable because no production data exists.
3. Document the procedure in the starter's `README.md` for future forks that may have applied V1 before bumping.

### 6. Tests move with the code they cover

- Unit tests follow the code into the starter.
- Integration tests live wherever the entity they exercise lives.
- E2E (Playwright) stays in the template.
- Konsist architecture tests are updated/expanded in both repos.

### 7. Each plan ships an independent starter version bump

`0.2.0` → `0.3.0` → … → `0.8.0`. The template's `gradle/libs.versions.toml` updates per migration. The template stays green at every step.

### 8. Local development uses `mavenLocal()` for in-flight starter changes

Starter is published to `~/.m2` via `./gradlew publishToMavenLocal` during a migration. The template resolves it from there until the migration lands. Real GitHub Packages release happens once the migration ships.

### 9. Open follow-up: Zitadel vs Clerk

`CLAUDE.md` says Zitadel; the code uses Clerk. The split-and-rename to `externalUserId` makes this decision orthogonal to the migration. Tracked as a separate task.

## The 8 plans

### Plan 0 — Rename `mvp-saas-template` → `kotlin-saas-template`

**Scope:** Repo rename, GitHub remote update, Kotlin package `org.granchi.mvpsaas` → `org.granchi.saastemplate`, `MvpSaasApplication` → `SaasTemplateApplication`, all doc/config references.

**Starter changes:** None.

**Done when:** Template builds, all tests pass, `CLAUDE.md` updated.

### Plan 1 — Add autoconfiguration scaffolding to starter

**Scope:** Create autoconfig infrastructure: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, a top-level `SaasStarterAutoConfiguration` umbrella, a `SaasStarterProperties` for `@ConfigurationProperties(prefix = "saasstarter")`. No bean moves yet — just lays the rails.

**Starter version:** 0.2.0

**Done when:** Template depends on 0.2.0, autoconfig class loads, smoke test confirms `saasstarter.*` properties bind.

### Plan 2 — Move `SessionConfig`

**Scope:** Redis-backed HTTP sessions move to starter as `SessionAutoConfiguration` with `@ConditionalOnMissingBean` and `@ConditionalOnProperty(prefix = "saasstarter.session", name = "enabled", matchIfMissing = true)`. Template loses `SessionConfig.kt`.

**Starter version:** 0.3.0

**Done when:** Session storage still works in template integration tests with the file removed.

### Plan 3 — Move `JobRunrConfig`

**Scope:** JobRunr config + `TenantJobFilter` wiring move to starter as `JobRunrAutoConfiguration`. Template loses `JobRunrConfig.kt`. Job dashboard properties bind via `saasstarter.jobs.*`.

**Starter version:** 0.4.0

**Done when:** Template can still schedule and run a tenant-aware job in integration tests.

### Plan 4 — Move `RedisConfig` (split)

**Scope:** Generic bits — `RedisTemplate`, JSON serialization, default `CacheManager` — move to `RedisAutoConfiguration` in starter. App-specific cache configurations (key names, TTLs) become properties:

```yaml
saasstarter:
  cache:
    tenant-by-user:
      ttl: 10m
    organization:
      ttl: 5m
    subscription:
      ttl: 5m
```

The template's `RedisConfig.kt` is removed.

**Starter version:** 0.5.0

**Done when:** Template caches behave identically; cache config moved to properties.

### Plan 5 — Move `WebMvcConfig` pattern

**Scope:** Interceptor registration mechanism (`TenantInterceptor` + `RateLimitInterceptor`) moves to `WebMvcAutoConfiguration` in starter. Apps declare path patterns via properties:

```yaml
saasstarter:
  tenant:
    path-patterns: ["/app/**", "/dashboard/**", "/billing/**", "/organization/**"]
  rate-limit:
    path-patterns: ["/api/**", "/billing/webhooks/**"]
```

Template loses `WebMvcConfig.kt`.

**Starter version:** 0.6.0

**Done when:** All currently-intercepted routes still get tenant context + rate limiting.

### Plan 6 — Move multi-tenant model

**Scope:**
- `Organization`, `Member` entities → starter (`Member.zitadelUserId` → `externalUserId`)
- `interface MemberRole` + `DefaultMemberRole` enum → starter
- `OrganizationRepository`, `OrganizationService`, `OrganizationValidations` → starter
- Flyway migrations for `organizations` and `members` tables → starter (`db/migration/saasstarter/V100__starter_baseline.sql`); see decision 5 for baseline handling
- Template's `V1__init.sql` is edited to remove the `organizations` and `members` CREATE TABLEs (subscriptions stay until Plan 7); Flyway configured with both locations
- Local dev databases reset (`docker compose down -v`) as part of this migration
- `MemberTenantResolver` stays in template
- `OrganizationController` stays in template, uses starter's service
- Tests for `OrganizationService` move to starter
- Integration tests for `OrganizationController` stay in template

**Starter version:** 0.7.0

**Done when:** Template only contains the controller + tenant resolver for org-related code; integration tests pass.

### Plan 7 — Move billing module

**Scope:**
- `Subscription` entity → starter (`stripeCustomerId` → `externalCustomerId`, `stripeSubscriptionId` → `externalSubscriptionId`)
- `SubscriptionRepository` → starter
- `interface BillingPlan` + `DefaultBillingPlan` enum → starter
- `BillingService` → starter, configurable via `saasstarter.billing.*` for plan-to-Stripe-price-ID mapping
- `StripeWebhookHandler` → starter
- Flyway migrations for `subscriptions` table → starter (`V101__starter_subscriptions.sql`); template's `V1__init.sql` further edited to remove subscriptions CREATE TABLE; local dev databases reset
- `BillingController` stays in template
- Tests for `BillingService`, webhook handler move to starter

**Starter version:** 0.8.0

**Done when:** Template only contains `BillingController` + plan price config; subscription lifecycle tests pass via starter.

## Cross-cutting concerns

### Testing strategy

- **Unit tests** move with the code they cover.
- **Integration tests** live in both repos:
  - Starter: integration tests for moved components (real DB, validates starter migrations apply cleanly).
  - Template: tests for app-domain code, plus a smoke test per migration confirming autoconfig wiring.
- **Konsist** rules in the template are updated each migration; rules in the starter expand to enforce the same conventions on starter code.
- **E2E (Playwright)** stays in the template only.

### Release coordination

For each migration:

1. Branch in the starter repo with code + tests.
2. `./gradlew publishToMavenLocal` to publish in-progress version to `~/.m2`.
3. Template switches to `mavenLocal()` resolution, updates `gradle/libs.versions.toml`.
4. Iterate locally until template tests pass.
5. Tag and release the starter version (push to GitHub Packages via existing CI).
6. Template switches back to GitHub Packages, commits the version bump.
7. Squash-merge both PRs.

### Version strategy

- Starter follows semver. Each migration is a **minor bump** because each adds new public API without breaking earlier API.
- After Plan 7, promote to 1.0.0 with a `CHANGELOG.md` entry summarizing the API surface.

### Documentation per migration

- **Starter:** `CHANGELOG.md` entry, `README.md` configuration section for new properties, KDoc on new public types.
- **Template:** `CLAUDE.md` updated to reflect the new starter/template boundary, version bump comment in `libs.versions.toml`.

### Plan dependencies

- **Plan 0** blocks nothing else but should land first.
- **Plan 1** (autoconfig scaffolding) blocks Plans 2–7.
- **Plans 2 and 3** are independent of each other.
- **Plans 4 and 5** are independent of each other.
- **Plan 6** has a soft dependency on Plan 4 (cache config); could land earlier with temporary cache duplication.
- **Plan 7** depends on Plan 6 (`Subscription` references `Organization`).

**Recommended sequence:** 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7. Linear, low coordination overhead, each ships independently.

## Final state of the template

```
app/src/main/kotlin/org/granchi/saastemplate/
├── SaasTemplateApplication.kt           # Spring Boot entry point
├── config/
│   ├── MemberTenantResolver.kt          # concrete impl of starter's TenantResolver
│   └── SecurityConfig.kt                # app-specific route authorization
├── billing/
│   └── BillingController.kt             # app URLs, uses starter's BillingService
├── organization/
│   └── OrganizationController.kt        # app URLs, uses starter's OrganizationService
├── imports/                             # unchanged: app-domain
│   └── Import.kt
├── analysis/                            # unchanged: app-domain
│   └── AnalysisPipeline.kt
└── dashboard/                           # unchanged: app-domain
    └── DashboardController.kt

app/src/main/resources/
├── application.yml                      # saasstarter.* properties + app config
├── db/migration/                        # app-only migrations: jobs, imports, analysis
└── templates/                           # Thymeleaf
```

The template's `config/` package shrinks from 6 files to 2. The remaining files are all about the parts that *should* differ per fork: the auth provider mapping and the route authorization policy.

Roughly 13 Kotlin files plus partial/full migration SQL move from template to starter.
