# Authentication

This template uses [Zitadel](https://zitadel.com) (self-hosted) for identity management and SSO. The key behaviors to understand when building on or forking it:

## Social login providers

Five providers are wired in: Google, GitHub, Microsoft/Entra, Apple, and Slack. Each is optional — buttons appear automatically when the matching `ZITADEL_DEV_*` credentials are set. See CLAUDE.md → "Enabling social login locally" for setup instructions and callback URLs.

**Slack-specific:** Slack's OIDC does not return `preferred_username`. The template injects the user's email as the username via a Zitadel Actions v2 `restCall` target on the `RetrieveIdentityProviderIntent` response (`ZitadelIdpIntentWebhookController`), so both the new-user completion form and same-email auto-link work.

## Identity linking policy

Zitadel auto-links a social login to an existing account when the IDP email matches a **local (password-based) Zitadel account**. This is by design:

- **Password account + social login (same email) → auto-linked.** The password account's email was verified by Zitadel directly, so linking a social provider to it is safe.
- **Two social logins (same email) → two separate accounts.** Zitadel does not auto-link two external IDPs by email claim alone. Both claims are unverified relative to each other — a third party who controls your email address on one platform could silently gain access to your account on another. The conservative default is correct for B2B SaaS.

**Practical consequence:** if a user signs up via Google and later tries Slack with the same email, they land in separate accounts. The safe resolution is to sign in with the first provider and link the second from Zitadel account settings. Consider surfacing this in onboarding copy if users are likely to use multiple providers.

**Configuring this:** Zitadel's login policy has an `autoLinking` setting (`none` / `username` / `email`). The template uses the safe default. Enabling IDP→IDP linking by email would be a deliberate security tradeoff — weigh it against your threat model before changing it.

## Actions v2 restCall target

The IDP-intent endpoint (`/internal/zitadel/idp-intent`) is a Zitadel Actions v2 **`restCall`** target registered on the `RetrieveIdentityProviderIntent` Response. This means Zitadel **replaces** the intent response with what this endpoint returns — the full `response` body must always be echoed back with any modifications applied. (A `restWebhook` target calls the same endpoint but discards the response body, so it cannot mutate intent data.)

Current side-effects applied by `ZitadelIdpIntentWebhookController`:

1. **Username injection** — fills `idpInformation.userName` (protojson camelCase) from the IDP email when blank (Slack omits `preferred_username`). Required for `AddIDPLink.UserName` validation to pass.
2. **Avatar capture** — when `rawInformation` contains a picture URL, persists it via `UserAccountService` so the in-app avatar is populated on first login.
3. **Microsoft/Entra binary avatar** — fetches `/me/photo/$value` from Microsoft Graph using the IDP OAuth token and stores the JPEG bytes in `AvatarStore`.

The target is registered with `interruptOnError: false`, so any failure in this endpoint never blocks social login.
