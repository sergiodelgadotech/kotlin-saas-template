# Manual testing guide

## When to run this

Run through the relevant sections before any release, and whenever you touch:

- Auth / Zitadel integration (login redirects, social providers, session routing)
- The member invitation flow (Zitadel user provisioning, invite-code email)
- Billing (Stripe Checkout, webhooks, portal)
- The SSE dashboard feed or HTMX-polling fragments

**Why this document exists.** The automated test suite deliberately stubs the real integrations:
`ZitadelAuthE2eTest` is gated off by default (`ZITADEL_E2E_ENABLED`), most e2e tests inject a
fake session via `TestAutoAuthFilter`, and `BillingE2eTest` hits a local Stripe stub. The flows
below require real external services and cannot be covered by automation without a full staging
environment — they must be verified by hand.

---

## Prerequisites

### Start the local stack

```bash
cp app/src/main/resources/application-local.yml.example \
   app/src/main/resources/application-local.yml   # fill in secrets once
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

- App → http://localhost:8080
- Zitadel admin console → http://localhost:8089/ui/console
- Jobrunr dashboard → http://localhost:8000

Spring Boot's docker-compose integration starts Postgres, Redis, and Zitadel automatically.

### Social login (for scenarios A3 and B2)

Register an OAuth app in your provider's developer console with callback URL
`http://localhost:3000/idps/callback` (port 3000 — login-proxy, not Zitadel core on 8089),
fill in the matching `ZITADEL_DEV_*` credentials in `.env`,
then re-seed Zitadel:

```bash
docker compose down -v && docker compose up -d
```

Each provider is optional and silently skipped when its env vars are absent. See CLAUDE.md →
"Enabling social login locally" for the full variable list.

### Stripe test mode (for scenarios C1–C5)

```bash
# Create a Pro product + price if you haven't yet
stripe products create --name="Pro"
stripe prices create --product=prod_XXX --unit-amount=7900 \
  --currency=eur --recurring.interval=month

# Forward webhook events to the local app (keep this terminal open during billing tests)
stripe listen --forward-to localhost:8080/webhooks/stripe
```

Add `STRIPE_API_KEY`, `STRIPE_PRICE_PRO`, and `STRIPE_WEBHOOK_SECRET` to `.env`.
Test card for Checkout: **4242 4242 4242 4242**, any future expiry, any CVC.

### Dev seed note

The local migration `V201__dev_seed.sql` creates "Dev Org" with an OWNER `local-dev-user` and an
active Starter subscription. This is the account `TestAutoAuthFilter` would auto-log you in as —
but in manual testing you exercise real Zitadel login, so use a real account or create one.

---

## Scenarios

Each scenario lists **Preconditions → Steps → Expected result**. Check the box when it passes.

---

### A. Authentication

#### A1 — Sign-up (brand-new user)

**Preconditions:** No existing Zitadel account for the email you'll use. App running.

**Steps:**
1. Open http://localhost:8080 in an incognito window.
2. Click **Sign up** (or navigate to http://localhost:8080/sign-up).
3. Complete the Zitadel hosted sign-up form (first name, last name, email, password).
4. Verify your email if Zitadel sends a verification code.

**Expected result:**
- You land on `/onboarding/organization` (no org exists yet).
- The Zitadel admin console shows the new user under Users.

- [ ] Pass

---

#### A2 — Sign-in routing by onboarding state

**Preconditions:** Three accounts exist at different stages:
- User A: authenticated but no org created yet.
- User B: org created, no subscription chosen.
- User C: org + active subscription (dev seed user, or one you completed onboarding for).

**Steps:**
1. Sign in as User A → observe redirect.
2. Sign out. Sign in as User B → observe redirect.
3. Sign out. Sign in as User C → observe redirect.

**Expected result:**
- User A lands on `/onboarding/organization`.
- User B lands on `/onboarding/plan`.
- User C lands on `/dashboard`.

- [ ] Pass

---

#### A3 — Social login (Google / GitHub / Microsoft / Apple)

**Preconditions:** At least one social provider configured in `.env` and Zitadel re-seeded (see prerequisites). Use an email that does NOT already have a Zitadel password account.

**Steps:**
1. Open http://localhost:8080/sign-in.
2. On the Zitadel hosted login page, click the social provider button (e.g. **Continue with Google**).
3. Complete the provider's OAuth consent screen.
4. On first connection: Zitadel may show an account-linking screen — confirm.

**Expected result:**
- You are redirected back to the app and routed correctly (onboarding if new user, dashboard if returning).
- The Zitadel admin console shows the user with an external identity linked.

- [ ] Pass (repeat for each configured provider)

---

#### A4 — Forgot password

**Preconditions:** A Zitadel user with a password (not social-only) exists.

**Steps:**
1. Open http://localhost:8080/sign-in.
2. On the Zitadel hosted page, click **Forgot password?**.
3. Enter the email address and submit.
4. Open the reset email (check the inbox, or inspect Zitadel console → Users → the user → Email).
5. Follow the reset link, set a new password, and sign in.

**Expected result:**
- Reset email arrives. After following the link and setting a new password, sign-in succeeds and you land in the app.

- [ ] Pass

---

#### A5 — Sign-out (Zitadel end-session)

**Preconditions:** Signed in to the app.

**Steps:**
1. Open the avatar dropdown in the top-right nav.
2. Click **Sign out**.
3. After redirect, try to access http://localhost:8080/dashboard directly.

**Expected result:**
- After sign-out, you land on http://localhost:8080/ (the home/landing page).
- Accessing `/dashboard` while signed out redirects you to the Zitadel login page.

- [ ] Pass

---

### B. Owner → user invitation + enrollment

#### B1 — OWNER invites a new user

**Preconditions:** Signed in as an OWNER. The email address to be invited does NOT yet have an account in this org. Resend API key configured (or check Zitadel console for the invite directly).

**Steps:**
1. Navigate to http://localhost:8080/organization/members.
2. Click **Invite member**.
3. Enter the invitee's email address and select role **Member**.
4. Submit the form.

**Expected result:**
- The members list page reloads with a success flash: "Invitation sent."
- Zitadel admin console → Users shows the invited user (or an existing user if the email was already registered in Zitadel) and the invite-code email was queued.
- The invitee's inbox receives an email with a Zitadel invite link.

- [ ] Pass

---

#### B2 — Invited user enrolls via social login (the headline flow)

**Preconditions:** B1 completed. Social login configured (see prerequisites). The invitee has access to a Google/GitHub/etc. account.

**Steps:**
1. Open the invite email in the invitee's inbox.
2. Click the invite link. Zitadel's invite-code form opens.
3. Enter the invite code from the email (or Zitadel may handle it automatically from the link).
4. On the Zitadel page that asks for name / account setup, choose **Continue with [Provider]** (social login) instead of entering a password.
5. Complete the OAuth consent for the social provider.
6. If prompted, confirm account linking.

**Expected result:**
- The invitee is redirected to the app and lands on `/dashboard` within the inviting organization.
- The member appears in http://localhost:8080/organization/members with the **Member** role.
- The nav shows the invitee's name from the social profile.
- Dashboard SSE feed (in a tab left open by the owner) shows a new activity entry for the invited member (see D1).

- [ ] Pass

---

#### B3 — MEMBER cannot invite (role authorization)

**Preconditions:** Signed in as a MEMBER (not OWNER or ADMIN).

**Steps:**
1. Navigate to http://localhost:8080/organization/members.
2. Click **Invite member** (the button should be present or the route accessible).
3. Fill in an email and submit the invite form.

**Expected result:**
- An error flash is shown: the action is forbidden for Members.
- No invitation is sent; Zitadel is not called.

- [ ] Pass

---

#### B4 — Validation errors on invite form

**Preconditions:** Signed in as OWNER or ADMIN.

**Steps:**
1. Go to http://localhost:8080/organization/members/invite.
2. Submit the form with an invalid email (e.g. `not-an-email`).
3. Resubmit with the email of a user who is already a member of this org.

**Expected result:**
- Invalid email: form re-renders with a field error on the email input.
- Already-member email: form re-renders with an appropriate error message.

- [ ] Pass

---

### C. Billing (real Stripe)

#### C1 — Pro plan checkout during onboarding

**Preconditions:** Brand-new user who has just created an org (at `/onboarding/plan`). Stripe CLI running (`stripe listen …`).

**Steps:**
1. On the plan picker page, click **Pro** and submit.
2. On the Stripe Checkout page, enter card `4242 4242 4242 4242`, any future date, any CVC, and complete the purchase.
3. Stripe redirects back to `/billing?success=true`.

**Expected result:**
- `/billing` shows the Pro plan card with status **Active** and a renewal date.
- The Stripe CLI terminal shows a `checkout.session.completed` event forwarded and a `200` response from the app.

- [ ] Pass

---

#### C2 — Starter plan (free path, no Stripe)

**Preconditions:** Brand-new user at `/onboarding/plan`.

**Steps:**
1. Click **Starter** (€0/mo) and submit.

**Expected result:**
- Redirected straight to `/dashboard`. No Stripe session created.
- `/billing` shows the Starter plan card.

- [ ] Pass

---

#### C3 — Upgrade Starter → Pro from billing page

**Preconditions:** Signed in as OWNER on a Starter plan.

**Steps:**
1. Navigate to http://localhost:8080/billing.
2. Click **Upgrade to Pro**.
3. Complete Stripe Checkout with test card.

**Expected result:**
- Same as C1: redirected to `/billing?success=true`, plan card shows Pro/Active.

- [ ] Pass

---

#### C4 — Cancel subscription via billing portal

**Preconditions:** Active Pro subscription. Stripe CLI running.

**Steps:**
1. Navigate to http://localhost:8080/billing.
2. Click **Manage subscription** → redirected to the Stripe billing portal.
3. In the portal, cancel the subscription.
4. Return to http://localhost:8080/billing.

**Expected result:**
- The billing page reflects the cancellation (status badge changes, e.g. shows cancellation date or **Canceled**).
- Stripe CLI shows the `customer.subscription.updated` / `customer.subscription.deleted` webhook received and handled (HTTP 200).

- [ ] Pass

---

#### C5 — Webhook-driven transition (payment failure simulation)

**Preconditions:** Active Pro subscription. Stripe CLI running.

**Steps:**
1. In a separate terminal, trigger a payment-failure event:
   ```bash
   stripe trigger invoice.payment_failed
   ```
2. Navigate to http://localhost:8080/billing.

**Expected result:**
- The Stripe CLI shows the event forwarded and a `200` response.
- The billing page status badge reflects the updated subscription state.

- [ ] Pass

---

### D. Real-time UI

#### D1 — SSE activity feed updates on member invite

**Preconditions:** Two browser windows: Tab 1 signed in as OWNER with `/dashboard` open. Tab 2 also signed in as OWNER.

**Steps:**
1. In Tab 1, keep the dashboard visible and watch the activity feed section.
2. In Tab 2, go to http://localhost:8080/organization/members/invite and invite a new user.

**Expected result:**
- Within a second or two of submitting the invite, Tab 1's activity feed updates without a page reload, showing the new member invite activity.
- No browser refresh required.

- [ ] Pass

---

#### D2 — Dashboard stats auto-refresh (30-second poll)

**Preconditions:** Signed in, on `/dashboard`.

**Steps:**
1. Open http://localhost:8080/dashboard.
2. Open browser DevTools → Network tab, filtered to XHR/Fetch.
3. Wait 30–35 seconds.

**Expected result:**
- A request to `/dashboard/stats` fires automatically every ~30 seconds.
- The stats section of the page updates in-place without a full reload.

- [ ] Pass

---

### E. Account propagation

#### E1 — Display name change propagates to Zitadel and nav

**Preconditions:** Signed in as any user.

**Steps:**
1. Navigate to http://localhost:8080/account.
2. Change the first or last name to something new and save.
3. Observe the top-right nav avatar/initials and display name.
4. Open the Zitadel admin console → Users → find the user.

**Expected result:**
- The nav (initials and display name) updates immediately on the next page load without signing out and back in.
- The Zitadel admin console shows the updated name for the user.
- The email field on the account page remains read-only.

- [ ] Pass

---

## Results log

| Scenario | Date | Tester | Result | Notes |
|---|---|---|---|---|
| | | | | |
