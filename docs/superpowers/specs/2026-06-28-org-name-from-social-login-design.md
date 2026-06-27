# Org Name from Social Login — Design

## Context

During onboarding, users must type their organization name into a plain text field. When
signing up via a social IDP (Google, Microsoft, GitHub), the IDP already knows which company
the user belongs to. This feature prefills — or offers as a dropdown — one or more org-name
suggestions derived from the IDP, so the user rarely has to type anything.

OIDC claims that reach the Spring Boot app come from Zitadel, not the upstream IDP directly.
The IDP access token (needed to call GitHub's orgs API, for example) is only available inside
Zitadel's action runtime. Therefore, all per-provider logic runs in Zitadel Actions; the app
reads a single custom claim `org_suggestions` from the OIDC token.

## Architecture

Three-stage pipeline:

```
IDP login
  │
  ▼
[Zitadel Post Authentication Action]  ← IDP access token available here
  • Google Workspace  → hd claim       → ["Acme"]
  • Microsoft Entra   → email domain   → ["Contoso"]
  • GitHub            → /user/orgs API → ["Acme", "OtherOrg", ...]
  • Apple / others    → nothing        → []
  └─ stores result as user metadata key "orgSuggestions"
  │
  ▼
[Zitadel Complement Token Action]     ← fires at every token issuance
  • reads "orgSuggestions" metadata
  • injects org_suggestions claim into OIDC token
  │
  ▼
[Spring Boot — OnboardingController GET /onboarding/organization]
  • reads oidcUser.getClaim<List<String>>("org_suggestions")
  • passes list to Thymeleaf model
  │
  ▼
[organization.html]
  • <input list="org-suggestions" th:value="${suggestions?.get(0) ?: ''}">
  • <datalist> populated from list — native combobox, user can type freely
  • degrades to plain input when list is empty
```

## Per-Provider Logic

### Google Workspace

Detection: `ctx.getClaim('hd')` is non-null (only present for Workspace accounts, never for
personal Gmail).

Suggestion: strip the TLD-suffix from the domain, capitalize and replace hyphens with spaces.
`acme.com` → `"Acme"`. Single-item list.

### Microsoft Entra ID

Detection: `ctx.getClaim('tid')` is non-null (Entra ID always includes the tenant GUID; personal
Microsoft accounts do not).

Suggestion: extract domain from the `email` / `preferred_username` claim, apply the same
domain-to-name transform. `bob@contoso.com` → `"Contoso"`. Single-item list.

No Graph API call is needed — the email claim is sufficient and avoids extra latency/permissions.

### GitHub

Detection: no `hd` and no `tid` → attempt GitHub API call.

The GitHub IDP scope in `init.py` gains `read:org`. The action calls:

```javascript
let http = require('zitadel/http')
http.fetch('https://api.github.com/user/orgs', {
    method: 'GET',
    headers: { 'Authorization': 'Bearer ' + ctx.accessToken,
                'Accept': 'application/vnd.github+json' }
}).json()  // → [{login, name, ...}, ...]
```

Suggestion list: `org.name` for each org (falling back to `org.login` when `name` is null).
May be empty (user belongs to no orgs), one item, or many.

Failure is silent — any exception or non-200 response leaves the list empty.

### Apple / Others

No provider-specific logic. Suggestions list is empty; field behaves as today.

## Zitadel Action Scripts

Two actions registered and wired in `docker/zitadel-init/scripts/init.py`:

**`extractOrgSuggestions`**
- Flow: External Authentication
- Trigger: Post Authentication
- Logic: provider detection + API calls + `api.v1.user.appendMetadata('orgSuggestions', JSON.stringify([...]))`

**`addOrgSuggestionsClaim`**
- Flow: Complement Token
- Trigger: Pre Userinfo creation, Pre Access Token creation
- Logic: reads `orgSuggestions` metadata, calls `api.v1.claims.setClaim('org_suggestions', suggestions)`

Helper used by both:
```javascript
function domainToName(domain) {
    const base = domain.split('.')[0];
    return base.charAt(0).toUpperCase() + base.slice(1).replace(/-/g, ' ');
}
```

## App Changes

### `OnboardingController.kt`

`organizationForm()` GET gains `@AuthenticationPrincipal oidcUser: OidcUser` and a `Model`
parameter. It reads `oidcUser.getClaim<List<*>>("org_suggestions")`, casts to
`List<String>`, and adds it to the model as `"suggestions"`. Returns the view name as before.

The POST handler is unchanged — it still reads `@RequestParam name` as typed/selected.

### `organization.html`

The `<input>` gains `list="org-suggestions"` and `th:value="${suggestions?.get(0) ?: ''}"`.
A `<datalist id="org-suggestions">` is added below it, rendered only when the list is non-empty:

```html
<input class="input input-bordered w-full"
       type="text" id="org-name" name="name"
       list="org-suggestions"
       th:value="${suggestions != null && !suggestions.isEmpty() ? suggestions[0] : ''}"
       placeholder="Acme Inc." autofocus required>
<datalist id="org-suggestions" th:if="${suggestions != null && !suggestions.isEmpty()}">
  <option th:each="s : ${suggestions}" th:value="${s}"/>
</datalist>
```

The existing submit-button disable script is untouched.

## UX Notes

- Adding `read:org` to GitHub scope means the GitHub OAuth consent screen shows
  "Read access to your organization membership". This is intentional and should be noted in the PR.
- The field remains fully editable regardless of suggestions — the user is never forced to use a
  suggestion.
- Suggestions are refreshed on every login (metadata is overwritten, not appended), so they stay
  current if a user joins a new GitHub org.

## Testing

**Automated:**
- Unit test for `OnboardingController` GET: mock `OidcUser` with `org_suggestions` claim, assert
  model contains the list; mock without claim, assert model has null/empty.
- Unit test for the domain-to-name transform logic (if extracted to a Kotlin helper).

**Manual (see `docs/manual-testing.md` for setup):**
- Sign up with a Google Workspace account → org-name field prefilled with company name.
- Sign up with a personal Gmail → field blank, normal placeholder shown.
- Sign up with a GitHub account that has orgs → dropdown lists org names, field editable.
- Sign up with a GitHub account with no orgs → field blank.
- Sign up via email/password (no IDP) → field blank.
- In all cases: typing a custom name and submitting works normally.
