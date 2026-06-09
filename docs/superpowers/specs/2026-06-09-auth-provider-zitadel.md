# Auth Provider Decision — Zitadel

**Date:** 2026-06-09
**Status:** Decided
**Repos affected:** `kotlin-saas-template`, `kotlin-saas-starter`

## Context

The starter/template split spec (`2026-05-07-starter-template-split-design.md`, §9 / line 83) deliberately deferred the auth provider choice and renamed all columns to provider-agnostic names (`externalUserId`, env vars `AUTH_JWKS_URL` / `AUTH_ISSUER`). After the split landed, the codebase had drifted in two directions:

- **Toward Zitadel** (actively maintained): `docker-compose.yml` runs Zitadel, `CLAUDE.md` documented it, the devcontainer uses it, a `/sign-out` handler was added, Renovate bumps the Docker image.
- **Toward Clerk** (accidental scaffolding leftovers): `application-test.yml` pointed at `test.clerk.accounts.dev`; the README said "Clerk (JWT validation)" and referenced `ClerkJwtFilter` (a class that does not exist).

The runtime decision is config-only post-split — no structural change needed to choose either provider.

## Decision

**Zitadel** is the auth provider for this template.

Deployment options: Zitadel Cloud (EU/US/Switzerland/Australia regions available), or self-hosted on any Railway/Hetzner/VPS infrastructure.

## Rationale

Four dimensions drove the choice:

1. **EU data residency** — Zitadel Cloud offers region selection (EU, US, CH, AU) at all tiers. Clerk has no regional hosting; it offers only Data Privacy Framework coverage for GDPR transfers, which is legally fragile post-Schrems II and insufficient for EU SMB sales pitches.

2. **Cost at B2B scale** — Zitadel Cloud Pro is $100/mo for 25k DAU with organizations, custom roles, and domain verification included. Clerk requires Pro ($25/mo) plus the B2B Authentication add-on ($100/mo) = $125/mo minimum before org domain verification is available. Zitadel self-hosted reduces auth cost to ~$30/mo infrastructure.

3. **Self-hosting / vendor independence** — Zitadel can be self-hosted. Clerk cannot. This matters for forks targeting regulated industries or customers who require data on their own infrastructure.

4. **DX parity on this stack** — Clerk's main competitive advantage is React drop-in components (`<SignIn>`, `<OrganizationSwitcher>`). This template uses Thymeleaf + HTMX, so that advantage does not apply. Both providers reduce to hosted login pages + server-side JWT validation, which is already implemented in the starter via `auth0:java-jwt` / `auth0:jwk`.

## License note

Zitadel core is **AGPL-3.0-only**. Apache 2.0 applies only to the `proto/`, `apps/docs/`, `apps/login/`, and packages directories (see [LICENSING.md](https://github.com/zitadel/zitadel/blob/main/LICENSING.md)).

Practical implication for forks:
- **Unmodified self-host** — AGPL obligations are minimal; link upstream source. No commercial license needed.
- **Modified self-host** — AGPL requires publishing changes. Alternatively, purchase a commercial license from Zitadel.

A commercial license is also available from Zitadel for forks that need to avoid AGPL obligations regardless of modification.

## Consequences

- Clerk references in `app/src/test/resources/application-test.yml` and `README.md` are removed (see template plan issue).
- `#62` (sign-in/sign-up flow) and `#72` (sign-out → Zitadel end-session redirect) are unblocked by this decision.
- A follow-up discussion opens in the starter: given the starter is opinionated on Postgres/Redis/Jobrunr/Stripe, should it also become opinionated about Zitadel and ship pre-built helpers (Zitadel JWT claims, end-session URL, webhook handler skeleton, role-mapping)? See starter companion plan issue.

## Cross-links

- Closes template issue #1 "discusion: decide between Zitadel and Clerk for auth"
- Template plan issue: see issue tracker
- Starter companion issue: see `kotlin-saas-starter` issue tracker
