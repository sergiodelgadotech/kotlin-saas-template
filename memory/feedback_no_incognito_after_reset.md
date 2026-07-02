---
name: feedback_no_incognito_after_reset
description: "After ./gradlew reset, no incognito needed to test fresh logins"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: eb63106a-f602-496b-bb8d-5ba9794c78e5
---

Don't tell the user to use an incognito/private window to test a first-time login after `./gradlew reset`.

**Why:** `reset` tears down and wipes the entire Zitadel instance (volumes cleared, re-seeded from scratch), so there is no existing user, session, or IDP link to collide with. A normal browser window behaves as a first-time user.

**How to apply:** When giving manual-test steps that start with a reset, just say "log in with <provider>" — omit the incognito instruction. Incognito is only relevant when testing against a *persisted* Zitadel state without a reset. See [[feedback_local_profile_default]] for the related bootRun default.
