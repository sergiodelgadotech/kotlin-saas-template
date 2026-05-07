# MVP SaaS Template

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
git clone https://github.com/granchi/mvp-saas-template
cd mvp-saas-template

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

## Deploying to Railway

1. Create a Railway project and add PostgreSQL + Redis services
2. Set environment variables in Railway dashboard (see `application.yml` for the full list)
3. Add `RAILWAY_TOKEN` to GitHub repository secrets
4. Push to `main` — GitHub Actions deploys automatically

## Adding your domain

Replace all occurrences of `org.granchi.mvpsaas` with your own package, and update:
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
