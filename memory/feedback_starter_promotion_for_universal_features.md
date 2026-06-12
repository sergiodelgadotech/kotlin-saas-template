---
name: feedback-starter-promotion-for-universal-features
description: "For SaaS features universal enough that every consumer will want them (profile editing, etc.), promote into kotlin-saas-starter immediately — don't wait for a second SaaS to validate."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: ee1eedf8-ca4a-4446-b6df-1453b31a5fde
---

When a planned change is something every B2B SaaS built on the starter would obviously want (e.g. user profile self-edit, password reset wiring, account deletion), put the transversal pieces into `kotlin-saas-starter` from the start rather than implementing them template-locally first.

**Why:** The CLAUDE.md guideline "propose moving it to the starter (but only after the same pattern shows up in a second SaaS)" is for *judgment calls* — features whose generality is uncertain. For features that are obviously universal (profile editing was the trigger), the wait-for-second-SaaS rule produces unnecessary template-local code that has to be migrated later. The user explicitly overrode the default for the profile-update case with "all SaaS will want the same feature."

**How to apply:** When sketching a plan that touches `auth/idp`, user/account management, billing primitives, or other clearly-cross-cutting concerns, default to putting the abstraction in the starter. Reserve template-local placement for genuinely product-specific code (specific domain modules like `imports`, `analysis`, etc.). When in doubt, ask the user — but lead with "I'll put this in the starter unless you object."
