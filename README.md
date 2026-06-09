# Kotlin SaaS Template

[![CI](https://github.com/sergiodelgadotech/kotlin-saas-template/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sergiodelgadotech/kotlin-saas-template/actions/workflows/ci.yml) [![starter stable](https://img.shields.io/github/v/release/sergiodelgadotech/kotlin-saas-starter?label=starter%20%28stable%29&color=blue)](https://github.com/sergiodelgadotech/kotlin-saas-starter/releases/latest) [![starter snapshot](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2Fsergiodelgadotech%2Fkotlin-saas-starter%2Frelease-please--branches--main--components--kotlin-saas-starter%2Fgradle.properties&search=version%3D%28.*%29&replace=%241-SNAPSHOT&label=starter%20%28snapshot%29&color=orange)](https://github.com/orgs/sergiodelgadotech/packages?repo_name=kotlin-saas-starter) [![current pin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fsergiodelgadotech%2Fkotlin-saas-template%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin-saas-starter&label=current%20pin&color=informational)](gradle/libs.versions.toml) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
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
| Auth | Zitadel (JWT validation, self-hostable, EU residency) |
| Payments | Stripe |
| Email | Resend |
| Monitoring | Sentry |
| Hosting | Railway |
| Static site | Cloudflare Pages |

## Architecture

Multi-tenant B2B SaaS with `organization_id` column isolation. Every authenticated request resolves the tenant via `TenantInterceptor` → `TenantContext`. Services and repositories always filter by tenant — never trust the caller.

```
JwtAuthFilter → TenantInterceptor → TenantContext → Service → Repository
```

## Getting started

### Develop in a devcontainer

The quickest way to get a fully-wired environment is to open the project in a devcontainer. It provisions JDK 25, Gradle, Postgres, Redis, Zitadel, and the Claude Code CLI automatically — no manual setup required.

**Prerequisites**

- Docker Desktop (or Docker Engine + Docker Compose v2)
- VS Code with the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-remote.remote-containers), Cursor, **or** JetBrains Gateway
- The following env vars exported in your host shell before opening the container:

  | Variable | Where to get it |
  |---|---|
  | `GITHUB_ACTOR` | Your GitHub username |
  | `GITHUB_TOKEN` | Personal access token with `read:packages` scope |
  | `AUTH_JWKS_URL` | Zitadel admin console. Inside the devcontainer use `host.docker.internal` instead of `localhost`, e.g. `http://host.docker.internal:8089/oauth/v2/keys` |
  | `AUTH_ISSUER` | Zitadel admin console. Inside the devcontainer use `host.docker.internal` instead of `localhost`, e.g. `http://host.docker.internal:8089` |
  | `ZITADEL_CLIENT_ID` | Zitadel console → your project → local-dev app → client ID (written to `docker/zitadel-init/.local-client-id` by the seed) |
  | `STRIPE_API_KEY` | Stripe dashboard |
  | `STRIPE_WEBHOOK_SECRET` | Stripe dashboard → Webhooks |
  | `STRIPE_PRICE_STARTER` | Stripe Price ID for Starter plan |
  | `STRIPE_PRICE_PRO` | Stripe Price ID for Pro plan |
  | `RESEND_API_KEY` | Resend dashboard |
  | `SENTRY_DSN` | Sentry dashboard |
  | `APP_BASE_URL` | Optional — defaults to `http://localhost:8080` |

**VS Code / Cursor**

Open the repo folder, then run the command **Dev Containers: Reopen in Container** (via the Command Palette or the green bottom-left button). VS Code will build and start the containers, run `./gradlew dependencies` to warm up the dependency cache, and copy `application-local.yml.example` to `application-local.yml` automatically.

**JetBrains Gateway**

Open Gateway → **Connect to Dev Container** → select the cloned repo folder. Gateway reads `.devcontainer/devcontainer.json` and spins up the same environment.

**Start the app**

Once inside the container, run:

```bash
./gradlew :app:bootRun
```

> **Do not** activate the `local` Spring profile inside the devcontainer. The database, Redis, and all secret env vars are already set by the container environment — `application-local.yml` is not needed and will override the correct values if the `local` profile is active.

**Notes**

- `claude` is available in the integrated terminal. Run it once after the container starts to authenticate.
- `remoteUser` is `vscode` — this is the built-in non-root user in the `mcr.microsoft.com/devcontainers/java` image. The name is unrelated to the VS Code editor and works identically with JetBrains Gateway and Cursor.
- `DATABASE_URL`, `REDIS_URL`, and all secret env vars are supplied by the container environment. The host-shell env vars listed in the prerequisites table are forwarded into the container by `remoteEnv` in `devcontainer.json`. Ensure those are set before opening the container.
- Zitadel starts in the background but is not a container startup dependency — it takes longer to initialise than Postgres and Redis. Wait for it to become healthy before testing auth flows.

### 1. Prerequisites (manual local setup)

- JDK 25
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

Edit `application-local.yml` with your Zitadel, Stripe and Resend keys.

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
- `infra/terraform.tfvars.example` → copy to `infra/terraform.tfvars` and fill in all secrets

## Observability

Logs, metrics, and traces are shipped via OpenTelemetry to **Grafana Cloud free tier** (10k Prometheus series, 50 GB logs, 50 GB traces, 14-day retention — forever-free).

### Grafana Cloud setup

1. Sign up at [grafana.com](https://grafana.com) and create a stack.
2. In your stack, go to **Connections → Add new connection → OpenTelemetry (OTLP)**.
3. Generate an API token and copy the OTLP endpoint URL.
4. Set two env vars in your Railway deployment:

| Variable | Value |
|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP endpoint from Grafana Cloud (e.g. `https://otlp-gateway-prod-eu-west-0.grafana.net/otlp`) |
| `OTEL_EXPORTER_OTLP_HEADERS` | `Authorization=Basic <base64(instanceId:token)>` |
| `APP_ENV` | `production` (switches Logback to JSON output; defaults to `local`) |
| `OTEL_TRACING_SAMPLING` | Sampling probability `0.0`–`1.0` (defaults to `1.0`) |

Traces, metrics, and structured logs (with `tenant_id`, `correlation_id`, `traceId` fields) will appear in Grafana within ~30 seconds of the first request.

### Alternative backends

The OTLP exporter is backend-agnostic. To switch to Axiom, New Relic, Honeycomb, or a self-hosted collector, change `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_EXPORTER_OTLP_HEADERS` — no code changes required.

### Production note

`/actuator/prometheus`, `/actuator/info`, and `/actuator/metrics` are permitted without authentication so Grafana Cloud's Prometheus scraper can reach them. In production, restrict these paths to your internal/monitoring network via Railway's private networking or a reverse proxy.

### Dashboards as code

The starter operational dashboard (four rows: HTTP traffic, auth/rate limiting, jobs/webhooks, infrastructure) is provisioned via the Grafana Terraform provider in `infra/`. To enable it on a fork:

1. Create a Grafana Cloud service-account token with **Editor** role: your stack → Administration → Users and access → Service accounts.
2. Find your Prometheus datasource UID: Connections → Data sources → click your Prometheus datasource → copy the UID from the URL (`/datasources/edit/<UID>`).
3. Copy `infra/terraform.tfvars.example` → `infra/terraform.tfvars` (gitignored) and fill in `grafana_url`, `grafana_api_key`, and `grafana_prometheus_datasource_uid` — or set them as workspace variables in Terraform Cloud.
4. `./gradlew :infra:apply` — the dashboard appears in Grafana within seconds.

The dashboard has a **Tenant** variable at the top. HTTP, auth, rate-limit, job, webhook, and lock panels all filter by it. JVM heap panels are global (heap is process-level, not per-tenant).

### Alerts as code

Ten operational alert rules are provisioned via `infra/grafana_alerts.tf` into a **"SaaS Template Alerts"** folder in Grafana. An email contact point is wired to a default notification policy. Rules are organised into six domain groups:

| Group | Rules |
|---|---|
| Auth | `HighJwtInvalidRate`, `JwtExpiredSpike` |
| RateLimit | `RateLimitDenialSpike` |
| Locks | `LockErrorRate`, `LockContentionHigh` |
| JobsWebhooks | `JobSchedulerErrors`, `WebhookProcessingErrors` |
| HTTP | `High5xxRate`, `HighP99Latency` |
| JVM | `JvmHeapPressure` |

To enable on a fork (requires the same Grafana vars from the Dashboards setup above):

1. Add `alert_email = "ops@yourdomain.com"` to `infra/terraform.tfvars`.
2. `./gradlew :infra:apply` — the rules and contact point appear in Grafana → Alerting within seconds.

> **Threshold tuning:** The thresholds are conservative starting points. After observing one week of baseline traffic in production, revisit each rule's PromQL `expr` — the rationale is in the comment above every rule in `infra/grafana_alerts.tf`.

## Zitadel app registration

### Production

1. Log in to your Zitadel console (`https://your-zitadel-domain/ui/console`).
2. Create a **project** (e.g. "My SaaS").
3. Inside the project, add a new **application** of type **Web**, then configure it:
   - **Auth method:** None (PKCE — no client secret required)
   - **Redirect URI:** `https://your-app-domain/login/oauth2/code/zitadel`
   - **Post-logout redirect URI:** `https://your-app-domain/`
4. Copy the generated **Client ID** to the `ZITADEL_CLIENT_ID` env var. Leave `ZITADEL_CLIENT_SECRET` empty.
5. Set `AUTH_ISSUER=https://your-zitadel-domain` and `AUTH_JWKS_URL=https://your-zitadel-domain/oauth/v2/keys`.

> **Forgot password?** Since `/sign-in` is an instant redirect to Zitadel's hosted login page, the "Forgot password?" link appears there automatically — no template-side wiring is needed.

### Local development

`zitadel-init` (Task 9, not yet implemented) will automate this step. For now, after `docker compose up -d` and once Zitadel has seeded, copy the client ID written to `docker/zitadel-init/.local-client-id` into `application-local.yml` as `ZITADEL_CLIENT_ID`. Leave `ZITADEL_CLIENT_SECRET` blank (PKCE, no secret).

## Environment variables

| Variable | Description |
|---|---|
| `DATABASE_URL` | Set automatically by Railway |
| `REDIS_URL` | Set automatically by Railway |
| `AUTH_JWKS_URL` | From Zitadel admin console |
| `AUTH_ISSUER` | From Zitadel admin console |
| `ZITADEL_CLIENT_ID` | OIDC client ID from Zitadel app registration (see above) |
| `ZITADEL_CLIENT_SECRET` | Leave empty — PKCE is used, no secret needed |
| `POST_LOGOUT_REDIRECT_URI` | Where to redirect after logout (defaults to `{baseUrl}/`) |
| `STRIPE_API_KEY` | From Stripe dashboard |
| `STRIPE_WEBHOOK_SECRET` | From Stripe dashboard → Webhooks |
| `STRIPE_PRICE_STARTER` | Stripe Price ID for Starter plan |
| `STRIPE_PRICE_PRO` | Stripe Price ID for Pro plan |
| `RESEND_API_KEY` | From Resend dashboard |
| `SENTRY_DSN` | From Sentry dashboard |
| `APP_BASE_URL` | e.g. `https://app.yourdomain.com` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Grafana Cloud OTLP endpoint (or any OTLP-compatible backend) |
| `OTEL_EXPORTER_OTLP_HEADERS` | Bearer/Basic auth header for the OTLP endpoint |
| `APP_ENV` | `production` for JSON logs; omit for plain local logs |
| `OTEL_TRACING_SAMPLING` | Trace sampling probability (default `1.0`) |

## Third-party notices

`NOTICE` at the repo root lists all runtime dependencies and their licenses.
It is regenerated automatically on every `./gradlew build`.
If you update a dependency version, commit the updated `NOTICE` alongside the version bump.
