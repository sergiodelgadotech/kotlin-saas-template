# Kotlin SaaS Template

[![CI](https://github.com/sergiodelgadotech/kotlin-saas-template/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sergiodelgadotech/kotlin-saas-template/actions/workflows/ci.yml) [![starter stable](https://img.shields.io/github/v/release/sergiodelgadotech/kotlin-saas-starter?label=starter%20%28stable%29&color=blue)](https://github.com/sergiodelgadotech/kotlin-saas-starter/releases/latest) [![starter snapshot](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2Fsergiodelgadotech%2Fkotlin-saas-starter%2Frelease-please--branches--main--components--kotlin-saas-starter%2Fgradle.properties&search=version%3D%28.*%29&replace=%241-SNAPSHOT&label=starter%20%28snapshot%29&color=orange)](https://github.com/orgs/sergiodelgadotech/packages?repo_name=kotlin-saas-starter) [![current pin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fsergiodelgadotech%2Fkotlin-saas-template%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin-saas-starter&label=current%20pin&color=informational)](gradle/libs.versions.toml)
<!-- Snapshot badge reads gradle.properties from the starter's release-please PR branch
     (release-please--branches--main--components--kotlin-saas-starter). If that branch
     doesn't exist (brief window after a release before the next conventional commit),
     the badge renders an error. Update the URL if release-please's branch naming changes. -->

Production-ready template for B2B SaaS products. Kotlin + Spring Boot + Thymeleaf + HTMX.

## Stack

| Layer | Technology |
|---|---|
| Backend | Kotlin + Spring Boot 3 |
| Templates | Thymeleaf + HTMX |
| CSS | Tailwind CSS + DaisyUI |
| Database | PostgreSQL (Spring Data JDBC + Flyway) |
| Cache | Redis |
| Auth | Clerk (JWT validation) |
| Payments | Stripe |
| Email | Resend |
| Monitoring | Sentry |
| Hosting | Railway |
| Static site | Cloudflare Pages |

## Architecture

Multi-tenant B2B SaaS with `organization_id` column isolation. Every authenticated request resolves the tenant via `TenantInterceptor` → `TenantContext`. Services and repositories always filter by tenant — never trust the caller.

```
ClerkJwtFilter → TenantInterceptor → TenantContext → Service → Repository
```

## Getting started

### 1. Prerequisites

- JDK 21
- Docker (for local PostgreSQL + Redis)
- Node.js (for Tailwind CLI)

### 2. Clone and configure

```bash
git clone https://github.com/sergiodelgadotech/kotlin-saas-template
cd kotlin-saas-template

# Copy and fill in your local config
cp app/src/main/resources/application-local.yml.example \
   app/src/main/resources/application-local.yml
```

Edit `application-local.yml` with your Clerk, Stripe and Resend keys.

### 3. Start local services

```bash
docker compose up -d
```

### 4. Run the app

```bash
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

Open http://localhost:8080

### 5. Run tests

```bash
# Unit + architecture (fast)
./gradlew :app:test

# Integration (requires Docker)
./gradlew :app:integrationTest

# E2E
./gradlew :app:e2eTest
```

## Project structure

```
├── app/          Spring Boot application
├── web/          Static landing page (Cloudflare Pages)
├── infra/        Terraform (Railway + Cloudflare)
└── .github/      CI/CD workflows
```

## Dependency updates

Renovate is configured (`.github/renovate.json`) to open PRs for outdated dependencies daily before 6 AM (Europe/Madrid).

**Setup required when forking:**
1. Install the [Renovate GitHub App](https://github.com/apps/renovate) and enable it for this repo.
2. Renovate authenticates against GitHub Packages automatically using the platform token — no extra secrets needed for `kotlin-saas-starter`.

**What gets updated:**
- `kotlin-saas-starter` → dedicated PR per release with title `chore(deps): bump kotlin-saas-starter to X.Y.Z`
- Other Gradle deps (minor/patch) → grouped into a single PR
- GitHub Actions, Docker images, Terraform providers → each grouped by type

## Project tracking

Active development is tracked on the [Starter/template split project](https://github.com/orgs/sergiodelgadotech/projects/1), which spans this repo and the [kotlin-saas-starter](https://github.com/sergiodelgadotech/kotlin-saas-starter) library.

## Deploying to Railway

1. Create a Railway project and add PostgreSQL + Redis services
2. Set environment variables in Railway dashboard (see `application.yml` for the full list)
3. Add `RAILWAY_TOKEN` to GitHub repository secrets
4. Push to `main` — GitHub Actions deploys automatically

## Adding your domain

Replace all occurrences of `org.granchi.saastemplate` with your own package, and update:
- `settings.gradle.kts` → `rootProject.name`
- `application.yml` → `spring.application.name`
- `infra/variables.tf` → `project_name` default

## Environment variables

| Variable | Description |
|---|---|
| `DATABASE_URL` | Set automatically by Railway |
| `REDIS_URL` | Set automatically by Railway |
| `AUTH_JWKS_URL` | From Zitadel admin console |
| `AUTH_ISSUER` | From Zitadel admin console |
| `STRIPE_API_KEY` | From Stripe dashboard |
| `STRIPE_WEBHOOK_SECRET` | From Stripe dashboard → Webhooks |
| `STRIPE_PRICE_STARTER` | Stripe Price ID for Starter plan |
| `STRIPE_PRICE_PRO` | Stripe Price ID for Pro plan |
| `RESEND_API_KEY` | From Resend dashboard |
| `SENTRY_DSN` | From Sentry dashboard |
| `APP_BASE_URL` | e.g. `https://app.yourdomain.com` |
