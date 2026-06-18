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
cp app/src/main/resources/application-local.yml.example \
   app/src/main/resources/application-local.yml
# fill in secrets
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

Spring Boot auto-starts Postgres, Redis, and Zitadel via Docker Compose (`spring-boot-docker-compose`).

The Jobrunr dashboard is at `http://localhost:8000`, Zitadel admin at `http://localhost:8089/ui/console`.

### Testing plans / billing locally

The **Starter** plan is free and never calls Stripe — choosing it during onboarding redirects straight to `/dashboard`. Only paid plans (currently Pro) need a real Stripe test price.

**One-time setup (test mode):**

The Stripe CLI picks up `STRIPE_API_KEY` from the environment (loaded by direnv from `.env`), so no explicit `stripe login` is needed in this project.

```bash
# 1. Create a Pro product + recurring price (€79/mo)
stripe products create --name="Pro"
stripe prices create --product=prod_XXX --unit-amount=7900 \
  --currency=eur --recurring.interval=month
# → note the returned price_... id

# 2. Add Stripe values to .env (gitignored, loaded by direnv → available to bootRun)
#    STRIPE_API_KEY=sk_test_...
#    STRIPE_PRICE_PRO=price_...    ← from above
#    STRIPE_WEBHOOK_SECRET=...     ← from step 3

# 3. In a second terminal, forward webhook events to the local app
stripe listen --forward-to localhost:8080/webhooks/stripe
# copy the printed whsec_... into STRIPE_WEBHOOK_SECRET in .env
```

Then `./gradlew :app:bootRun --args='--spring.profiles.active=local'` as usual.
Choosing **Pro** in onboarding will redirect to a real Stripe Checkout test page;
use card `4242 4242 4242 4242`.

### Agent sandboxes (`sbx`)

For parallel auto-mode agent runs (Claude Code with `--dangerously-skip-permissions`, multiple branches in flight), use [Docker Sandboxes](https://docs.docker.com/ai/sandboxes/) instead of the local `docker compose` setup. Each sandbox is a microVM with its own kernel, virtio-fs share of the worktree, nested Docker (so `testcontainers` and `docker compose up` work), pre-baked Java 25 / Node / Claude Code, and a network proxy that injects host-stored secrets without exposing them inside the VM. YOLO/`--dangerously-skip-permissions` is on by default; the KVM boundary is what makes that safe.

**One-time host setup:**

```bash
gh auth token | sbx secret set -g github       # stored globally, proxy MITMs api.github.com
# anthropic secret usually already set via `sbx secret set -g anthropic --oauth`
```

**Spawn a sandbox for a feature:**

```bash
sbx create claude . ../kotlin-saas-starter \
  --branch auto \
  --name kt-saas-<short-name> \
  -m 16g --cpus 8
```

This creates a git worktree at `.sbx/kt-saas-<short-name>-worktrees/sandbox-kt-saas-<short-name>/` and mounts the sibling `kotlin-saas-starter` as a second workspace.

**Run an agent / open a shell:**

```bash
sbx run kt-saas-<short-name>                                              # launches Claude Code
sbx exec kt-saas-<short-name> bash                                        # interactive shell

# Build/test commands need STARTER_PATH for composite build (see below):
sbx exec -e STARTER_PATH=/var/home/serandel/Projects/kotlin-saas-starter \
  kt-saas-<short-name> ./gradlew :app:test
sbx exec -e STARTER_PATH=/var/home/serandel/Projects/kotlin-saas-starter \
  kt-saas-<short-name> ./gradlew :app:integrationTest
```

`STARTER_PATH` points the composite build at the sibling repo's host-absolute path. The worktree under `.sbx/.../<branch>` is too deep for the relative `../kotlin-saas-starter` to resolve, so `settings.gradle.kts` looks at `STARTER_PATH` first.

**Tear down:**

```bash
sbx rm kt-saas-<short-name>     # removes sandbox + worktree + branch
```

**Known limitation — `maven.pkg.github.com` Basic auth:** `sbx`'s built-in `github` secret injects auth for `api.github.com` but not for `maven.pkg.github.com`. The `STARTER_PATH` composite build sidesteps this — Gradle never touches GitHub Packages. The proper fix is a project-specific `sbx kit` declaring a `serviceAuth` rule for `maven.pkg.github.com` with `Basic %s` format (verified working end-to-end against this repo, just not yet captured as a committed kit). Until that kit lands, use `STARTER_PATH` or prime `~/.gradle/caches` on host and mount it in.

**Known limitation — workspace mount exposes `.env`:** `sbx create claude .` mounts the entire git root via virtio-fs, including gitignored files. That means `.env`, `.devcontainer/.env`, `.cloudflared.env`, and any other secret file at the workspace root are *readable* from inside the sandbox at their host paths, even though they're not in the worktree itself. They are **not** auto-loaded into the agent's environment (no `direnv` in the image, `sbx` doesn't source them), but an agent could `cat` them. Two real mitigations: (a) move secret files outside the workspace tree and update `docker-compose.yml`'s `env_file:` references, or (b) replace each external service credential with a project `sbx kit` declaring `proxyManaged` env vars + `serviceAuth` rules (same pattern as the GitHub/Anthropic built-ins). Until you do one of these, the `--dangerously-skip-permissions` blast radius for sandbox agents includes whatever's in your `.env` files.

**Known limitation — nested Docker has its own image cache:** the sandbox's nested Docker daemon has an empty image store on first start. The host's Podman/Docker image cache is not shared, so the first `docker compose up` (or Spring Boot's docker-compose auto-start during `bootRun`) inside a fresh sandbox pulls every referenced image — `postgres:18-alpine`, `redis:8-alpine`, `ghcr.io/zitadel/zitadel:v4.15.1`, `ghcr.io/zitadel/zitadel-login:latest`, `python:3.14-alpine`, `cloudflare/cloudflared:latest` — totalling several GB and several minutes per cold sandbox. Subsequent runs in the *same* sandbox reuse the cache (persists across `sbx stop`/`start`). Likely mitigations to revisit: bake the project's commonly-used images into a per-project `sbx template`, or wait for `sbx` to add an image-store passthrough feature. For now, plan for the cold-start cost or warm a long-lived sandbox up front.

**Known limitation — sandbox auto-stops when no exec session is active:** detaching a long-running process via `sbx exec -d` is not enough by itself; the sandbox goes dormant after a period with no active session, killing detached processes (we lost a `bootRun` this way mid-Gradle-config). The normal `sbx run` interactive flow doesn't hit this because the foreground attach holds the session open. For automated/background workloads (e.g. running `bootRun` headless while you poll a port from the host), hold an explicit keepalive: `nohup sbx exec <name> sleep 7200 > /dev/null 2>&1 &` on the host while the work runs, then tear it down when done.

**Known limitation — `bootRun` inside the sandbox is blocked by `zitadel-init` networking:** the project's `zitadel-init` uses `network_mode: host` with `ZITADEL_URL=http://localhost:8089` so its Python script's HTTP requests carry the `Host: localhost:8089` header that Zitadel's vhost-based OIDC routing requires (`zitadel:8080` Host returns 404 on OIDC routes — fixed deliberately in commit `3c6a709`). Inside the sandbox this fails three ways: (1) `--network host` containers do not actually share the sandbox's microVM netns — `127.0.0.1:8089` is **connection refused** from such a container; (2) bridge networking with `extra_hosts: ["localhost:host-gateway"]` adds `172.x.x.1 localhost` correctly but the published port is unreachable from bridge containers in nested Docker (`HTTP 000`); (3) bridge networking with service-name URL `http://zitadel:8080` is reachable but returns 404 because Zitadel rejects the Host. Result: `zitadel-init` exits 1, Spring Boot's docker-compose lifecycle aborts, `bootRun` fails. Unit + integration tests are unaffected because `testcontainers` spawns ephemeral containers on its own bridge. Realistic future fixes: (a) modify `init.py` to send `Host: localhost:8089` explicitly while connecting via the bridge URL; (b) add a socat/nginx sidecar that rewrites the Host header; (c) configure Zitadel to accept additional `ZITADEL_EXTERNALDOMAIN` aliases for the service-name URL.

### Composite build

When `../kotlin-saas-starter` is checked out alongside this repo, `settings.gradle.kts` automatically substitutes it for the published Maven artifact. Edits in the sibling repo are picked up by the next template build — no `publishToMavenLocal` step required.

The `STARTER_PATH` environment variable takes precedence over the relative path — set it when you need to point the composite build at a non-sibling location (e.g. from a deep worktree inside an agent sandbox).

Without either a `STARTER_PATH` or a sibling directory (forks, CI), the `includeBuild` block is dormant and the starter resolves from GitHub Packages as a normal Maven dependency.

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

- **Project board:** <https://github.com/orgs/sergiodelgadotech/projects/1> ("Starter/template split") — surfaces issues from this repo and `kotlin-saas-starter` together. The custom `Plan` field groups items by plan number.
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
