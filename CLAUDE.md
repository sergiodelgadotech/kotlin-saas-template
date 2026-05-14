# CLAUDE.md — kotlin-saas-template

This file gives Claude Code the context to work on this repository effectively. Read it first.

## Workflow Conventions

- Use trunk-based development; do NOT propose feature branches.
- `kotlin-saas-starter` lives at `../kotlin-saas-starter` (real local clone). Do NOT clone fresh into `/tmp`.

## What this repo is

`kotlin-saas-template` is a **complete Spring Boot application** that serves as the canonical example of a B2B SaaS built on top of `kotlin-saas-starter`. It is meant to be:

1. **Forked when starting a new SaaS** — the starting skeleton for any new product
2. **A reference implementation** — when in doubt about how to wire something, look here first
3. **The dogfood** of the starter library — if a pattern works in this template, it can be promoted to the starter

Critically, **this template is not actively maintained per-fork**. Once you fork it for a new SaaS, that SaaS evolves independently. Updates to the template come from updates to `kotlin-saas-starter` (the library it depends on).

## Relationship with kotlin-saas-starter

```
kotlin-saas-starter    →    Maven artifact at GitHub Packages
                                    ↑
                                    │ implementation("org.granchi:kotlin-saas-starter:X.Y.Z")
                                    │
                              kotlin-saas-template
                              (this repo)
```

The starter provides the transversal infrastructure:
- `TenantContext`, `TenantInterceptor`, `TenantResolver` interface
- `JwtAuthFilter`
- `RedisLockService`, `RateLimiter`, rate-limit interceptor
- `JobSchedulerService`, `TenantJobFilter`
- Validation helpers, `GlobalExceptionHandler`

The template provides:
- A concrete `MemberTenantResolver` implementation
- The `WebMvcConfig` that wires the library's interceptors to app-specific paths
- All domain code: `organization`, `billing`, `imports`, `analysis`
- Infrastructure: `application.yml`, Flyway migrations, Docker, Terraform, GitHub Actions

When working in this repo, **do not duplicate code from the starter**. If you need a helper that already exists in `kotlin-saas-starter`, import it. If something new appears here that feels transversal, propose moving it to the starter (but only after the same pattern shows up in a second SaaS).

## Tech stack

- **Backend**: Kotlin + Spring Boot 3.4
- **Templates**: Thymeleaf + HTMX
- **CSS**: Tailwind + DaisyUI (Bulma is plan B)
- **Database**: PostgreSQL with Spring Data JDBC, Flyway migrations
- **Cache / queue / locks**: Redis
- **Async jobs**: Jobrunr (PostgreSQL-backed)
- **Auth**: Zitadel (self-hosted, EU data residency)
- **Payments**: Stripe
- **Email**: Resend
- **Monitoring**: Sentry
- **Hosting**: Railway (app + Postgres + Redis), Cloudflare Pages (landing)
- **Data analysis**: Kotlin DataFrame + Smile (OLS regression)

## Project structure

```
app/
├── src/main/kotlin/org/granchi/saastemplate/
│   ├── SaasTemplateApplication.kt
│   ├── config/             ← MemberTenantResolver, WebMvcConfig, SecurityConfig, RedisConfig, ...
│   ├── organization/       ← multi-tenant: Organization, Member entity + Service + Controller
│   ├── billing/            ← Stripe webhooks, Subscription, BillingService
│   ├── imports/            ← async file imports via Jobrunr (example domain)
│   ├── analysis/           ← OLS regression pipeline (example domain)
│   └── dashboard/          ← landing dashboard with HTMX + SSE
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/       ← Flyway migrations (V1, V2, V3)
│   ├── templates/          ← Thymeleaf templates
│   └── static/
├── src/test/kotlin/        ← unit, integration, e2e, architecture tests
└── build.gradle.kts

web/                        ← static landing page → Cloudflare Pages
infra/                      ← Terraform for Railway + Cloudflare DNS + WAF
.github/workflows/          ← CI/CD pipelines
docker-compose.yml          ← local dev: Postgres, Redis, Zitadel
```

## How to work in this repo

### Local development

```bash
docker compose up -d   # Postgres, Redis, Zitadel
cp app/src/main/resources/application-local.yml.example \
   app/src/main/resources/application-local.yml
# fill in secrets
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

The Jobrunr dashboard is at `http://localhost:8000`, Zitadel admin at `http://localhost:8089/ui/console`.

### Tests

```bash
./gradlew :app:test               # unit + architecture
./gradlew :app:integrationTest    # Testcontainers
./gradlew :app:e2eTest            # Playwright
```

### Authenticating to GitHub Packages

The library `kotlin-saas-starter` is published to GitHub Packages. To consume it:

```bash
# Either set environment variables:
export GITHUB_ACTOR=your-username
export GITHUB_TOKEN=ghp_xxx   # personal access token with read:packages

# Or add to ~/.gradle/gradle.properties:
gpr.user=your-username
gpr.token=ghp_xxx
```

The token only needs `read:packages` scope.

### Bumping the starter version

When `kotlin-saas-starter` releases a new version, update it in `gradle/libs.versions.toml`:

```toml
[versions]
kotlin-saas-starter = "0.2.0"   # ← bump here
```

Read the starter's `CHANGELOG.md` for breaking changes.

## Planning workflow

Implementation plans live as **GitHub issues**, not markdown files in the repo. Specs stay as frozen design records in `docs/superpowers/specs/`; plans are tracked at:

- **Project board:** <https://github.com/orgs/SergioDelgado-tech/projects/1> ("Starter/template split") — surfaces issues from this repo and `kotlin-saas-starter` together. The custom `Plan` field groups items by plan number.
- **Labels:** plan issues carry `plan` and `starter-split`; cross-repo plans also carry `cross-repo` and a `> **Companion:**` blockquote at the top of the body linking the partner issue in the other repo.
- **Sequencing:** each plan issue has a `### Blocked by` task list whose items auto-check when the referenced issues close.

When a new plan is needed, create the issue (or pair of issues for cross-repo work) — don't write a new markdown plan file. Specs remain markdown in `docs/superpowers/specs/`.

## Architectural conventions

These conventions are checked by Konsist tests in `src/test/kotlin/.../architecture/`:

1. **Multitenancy** — every domain entity has `organizationId`. The `TenantInterceptor` (from the starter) sets `TenantContext` automatically. Services and repositories must always filter by tenant.
2. **No HTTP in services** — controllers parse requests, services hold business logic, repositories do queries. Konsist enforces this.
3. **No direct repository access from controllers** — always through services.
4. **`TenantContext` is for services, not controllers** — controllers don't read it; the interceptor sets it before they're invoked.
5. **Webhook controllers don't use `TenantContext`** — they have no authenticated user.
6. **Validation lives in two layers**: `@Valid` (Jakarta) at HTTP boundary, Konform inside services for business rules.

## Things Claude can do freely

- Implement new domain modules (entity, repository, service, controller)
- Add Flyway migrations (always with sequential `V*__name.sql` numbering)
- Write tests for existing code
- Update Thymeleaf templates and HTMX interactions
- Improve documentation

## Things Claude should NOT do without asking

- Changing how the starter library is consumed (e.g. switching to a different artifact)
- Modifying the multi-tenant isolation strategy — ask first
- Adding new external service integrations (new SaaS dependencies)
- Reorganizing the package structure
- Modifying Konsist architecture rules
- Changing the deployment target (Railway → something else)

## Deployment

Push to `main` triggers GitHub Actions which deploys to Railway. Web (landing) deploys independently to Cloudflare Pages on push to `main` paths under `web/`.

Secrets needed in GitHub Actions:
- `RAILWAY_TOKEN` — for backend deployment
- `CLOUDFLARE_API_TOKEN` — for static site deployment

Environment variables in Railway dashboard:
- `DATABASE_URL`, `REDIS_URL` — auto-set by Railway services
- `AUTH_JWKS_URL`, `AUTH_ISSUER` — Zitadel
- `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_*` — Stripe
- `RESEND_API_KEY` — email
- `SENTRY_DSN` — monitoring
- `APP_BASE_URL` — `https://app.yourdomain.com`
