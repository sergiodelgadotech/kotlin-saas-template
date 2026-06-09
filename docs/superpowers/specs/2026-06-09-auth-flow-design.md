# Auth Flow Design — Sign-in / Sign-up / Sign-out / Callback

**Date:** 2026-06-09
**Status:** Approved
**Repos affected:** `kotlin-saas-starter`, `kotlin-saas-template`
**Closes:** template #62, template #72 (end-session redirect resolved as a byproduct)

## Context

The template has JWT validation wired via the starter's `JwtAuthFilter`, but no user-facing auth flow. `/sign-in`, `/sign-up`, and `/sign-out` go nowhere. Zitadel runs in `docker-compose.yml` but no OIDC application is registered and no callback handler exists. `MemberTenantResolver` silently returns null for unknown users, leaving first-time sign-ups unhandled.

Auth provider is Zitadel (see `2026-06-09-auth-provider-zitadel.md`). The starter is opinionated on Zitadel the same way it is opinionated on Postgres, Redis, Jobrunr, and Stripe.

## Approach

**Spring Security OAuth2 Client** (`spring-boot-starter-oauth2-client`) handles the OIDC Authorization Code flow with PKCE. Spring manages the authorize redirect, state/nonce, code exchange, token storage in session, and OIDC logout. The existing `JwtAuthFilter` stays in place for future Bearer-token API clients and is unaffected by this change.

A new `ZitadelSessionBridgeFilter` in the starter bridges the two auth paths: for browser-session requests it reads `OidcUser.subject` from Spring's `SecurityContext` and sets the `auth_user_id` request attribute, so `TenantInterceptor` and `MemberTenantResolver` continue working without modification.

## Request Flows

### Sign-in / Sign-up

```
Browser GET /sign-in  (or /sign-up)
  → AuthController 302 → /oauth2/authorization/zitadel
  → Spring builds authorize URL (state, nonce, PKCE verifier stored in session)
  → Browser redirects to Zitadel hosted login UI
  → User authenticates (email/password, Google, Microsoft, etc.)
  → Zitadel 302 → /login/oauth2/code/zitadel?code=…&state=…
  → Spring OAuth2LoginAuthenticationFilter: validates state, exchanges code for tokens via PKCE
  → ZitadelAuthenticationSuccessHandler:
      existing member  → 302 /dashboard
      new user (null org) → 302 /organization/new
```

`/sign-in` and `/sign-up` are both instant-redirect controller endpoints — no Thymeleaf template. Both redirect to `/oauth2/authorization/zitadel`. Zitadel's hosted login UI shows a registration link for new users; a `prompt=create` customization can be added later if a dedicated sign-up URL is needed.

### Subsequent browser requests

```
Browser GET /dashboard  (session cookie present)
  → SecurityContextPersistenceFilter restores OAuth2AuthenticationToken from session
  → JwtAuthFilter: no Bearer header → no-op
  → ZitadelSessionBridgeFilter: reads OidcUser.subject → sets auth_user_id attribute
  → TenantInterceptor → MemberTenantResolver → TenantContext populated
  → Controller runs normally
```

### Sign-out

```
Browser POST /sign-out  (CSRF token in form body)
  → Spring logout: invalidates session, deletes SESSION + JSESSIONID cookies
  → OidcClientInitiatedLogoutSuccessHandler:
      302 → Zitadel end_session_endpoint
            ?id_token_hint=…&post_logout_redirect_uri=http://localhost:8080/
  → Zitadel clears its session → 302 back to /
```

`nav.html` sign-out changes from `<a href="/sign-out">` (GET, currently broken) to a CSRF-protected POST form button styled to match the existing dropdown item.

## Components

### Starter: ZitadelSessionBridgeFilter

`OncePerRequestFilter` registered after `JwtAuthFilter`. Logic:

1. If `auth_user_id` request attribute is already set (Bearer token path) → skip.
2. If `SecurityContextHolder` authentication is `OAuth2AuthenticationToken` with `OidcUser` principal → set `auth_user_id = oidcUser.subject`.
3. Otherwise → no-op (unauthenticated request).

Autoconfigured under `@ConditionalOnClass(OidcUser::class)` so it is absent in starters that do not pull in `spring-security-oauth2-client`.

### Starter: OidcClientInitiatedLogoutSuccessHandler bean

Autoconfigured (same conditional) using `saasstarter.security.post-logout-redirect-uri`. Template `SecurityConfig` injects it as a bean — no manual construction needed.

### Starter: SaasStarterProperties.Security — new field

```kotlin
val postLogoutRedirectUri: String = "{baseUrl}/"
```

Env var: `POST_LOGOUT_REDIRECT_URI` (optional; defaults to site root).

### Template: AuthController

```
GET /sign-in  → redirect /oauth2/authorization/zitadel
GET /sign-up  → redirect /oauth2/authorization/zitadel
```

No Thymeleaf templates. Both endpoints are public (added to SecurityConfig allowlist).

### Template: ZitadelAuthenticationSuccessHandler

`SimpleUrlAuthenticationSuccessHandler` subclass. On authentication success, reads `OidcUser.subject`, calls `memberRepository.findOrganizationIdByUserId(userId)`:
- Non-null → redirects to `/dashboard`
- Null (new user) → redirects to `/organization/new`

Registered in `SecurityConfig` via `.oauth2Login { it.successHandler(handler) }`.

### Template: SecurityConfig — changes

- Add `.oauth2Login { successHandler, defaultLoginPage("/sign-in") }`
- Add `.logout { logoutUrl("/sign-out"), logoutSuccessHandler(oidcLogoutSuccessHandler), deleteCookies("SESSION", "JSESSIONID"), invalidateHttpSession(true) }`
- Add `/sign-in` and `/sign-up` to the public path allowlist
- Keep `JwtAuthFilter` registration unchanged
- Add `ZitadelSessionBridgeFilter` after `JwtAuthFilter`

### Template: application.yml — new block

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          zitadel:
            client-id: ${ZITADEL_CLIENT_ID}
            client-secret: ${ZITADEL_CLIENT_SECRET:}   # optional — left blank when using PKCE
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/zitadel"
            scope: openid, profile, email
        provider:
          zitadel:
            issuer-uri: ${AUTH_ISSUER}   # already declared; Spring discovers all endpoints from here

saasstarter:
  security:
    post-logout-redirect-uri: ${POST_LOGOUT_REDIRECT_URI:{baseUrl}/}
```

`AUTH_ISSUER` already exists. New env vars: `ZITADEL_CLIENT_ID`, `ZITADEL_CLIENT_SECRET` (optional).

`application-local.yml.example` gets:
```yaml
ZITADEL_CLIENT_ID: local-dev-client   # written by zitadel-init on first run
ZITADEL_CLIENT_SECRET:                # leave blank for PKCE
```

### Template: docker-compose Zitadel seed

Two new additions to the `auth` compose profile:

**`docker/zitadel-init/setup.yaml`** — mounted into the Zitadel container. Declares a machine user (`zitadel-init-sa`) and instructs Zitadel to generate a JSON key file for it during the init phase. The key file is written to a named volume (`zitadel-init-keys`).

**`zitadel-init` service** — one-shot (`restart: "no"`), depends on `zitadel` health. Mounts `zitadel-init-keys` (read) and `docker/zitadel-init/scripts` (read). `init.sh`:

1. Reads `zitadel-init-sa` key file from the shared volume.
2. Signs a JWT assertion (RS256) and exchanges it for an admin access token via Zitadel's JWT profile grant (`urn:ietf:params:oauth:grant-type:jwt-bearer`).
3. Creates project `kotlin-saas-template` (idempotent — skips if exists).
4. Creates OIDC web app with:
   - `app_type: WEB`, `auth_method_type: NONE` (PKCE, no client secret)
   - `dev_mode: true` (allows localhost redirect URIs)
   - `redirect_uris: ["http://localhost:8080/login/oauth2/code/zitadel"]`
   - `post_logout_redirect_uris: ["http://localhost:8080/"]`
   - `response_types: [CODE]`, `grant_types: [AUTHORIZATION_CODE]`
5. Creates test user `test@example.com / Test1234!` for E2E tests (idempotent).
6. Writes client ID to `docker/zitadel-init/.local-client-id` (gitignored) and prints a one-line copy instruction to stdout.

Script uses only `curl` and `openssl` (available in `curlimages/curl`) — no extra tooling.

## Testing

### Unit tests (app:test)

- `ZitadelAuthenticationSuccessHandlerTest` — mock `MemberRepository`: existing member → asserts redirect target `/dashboard`; null return → asserts redirect target `/organization/new`.
- `ZitadelSessionBridgeFilterTest` (starter) — verify `auth_user_id` is set from `OidcUser.subject` when Bearer is absent; verify it is skipped when `auth_user_id` already present; verify no-op when unauthenticated.

### Integration tests (app:integrationTest)

MockMvc with Spring Security test support:

- Unauthenticated GET `/dashboard` → 302, Location contains `/oauth2/authorization/zitadel` or login page.
- `oidcLogin()` post-processor → GET `/dashboard` → 200.
- `oidcLogin()` post-processor → POST `/sign-out` (with CSRF) → 302, Location contains Zitadel's `end_session_endpoint`.
- `oidcLogin()` with unknown `sub` → `ZitadelAuthenticationSuccessHandler` redirects to `/organization/new`.

### E2E tests (app:e2eTest)

Gated by `ZITADEL_E2E_ENABLED=true` (Playwright test is skipped if the env var is absent, so CI without a live Zitadel is unaffected).

Flow:
1. Navigate to `/sign-in` → Playwright lands on Zitadel hosted login.
2. Fill `test@example.com / Test1234!` (seeded by `zitadel-init`).
3. Assert redirect to `/organization/new` — the test user has a Zitadel account but no `Member` row, so the first-time path is always exercised. The `/dashboard` path is covered once the org creation wizard is implemented.
4. POST `/sign-out` → assert final URL is `/`.

## Out of scope

- `/organization/new` onboarding wizard implementation — tracked separately (TBD ticket).
- `prompt=create` differentiation for `/sign-up` — Zitadel's hosted login shows a registration link; dedicated sign-up URL with `prompt=create` is a follow-up.
- Refresh token handling — Spring stores the refresh token in the session automatically; explicit rotation logic is a future concern.
- Public API Bearer-token auth — `JwtAuthFilter` stays for this; a dual filter-chain (Resource Server + OAuth2 Client) is a future addition when an API is built.
- Production Zitadel registration — documented in README as manual steps; the seed script covers local dev only.

## Cross-links

- Auth provider decision: `2026-06-09-auth-provider-zitadel.md`
- Resolves template #62 and #72
- Starter companion work: `kotlin-saas-starter` (new autoconfiguration, `ZitadelSessionBridgeFilter`)
