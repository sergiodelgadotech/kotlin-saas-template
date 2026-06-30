---
name: feedback-plan-location
description: "Implementation plans go in GitHub issues, not markdown files in the repo"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 79cfca33-91bd-45fc-ac88-e5f2f71b095d
---

Do not create markdown plan files in `docs/superpowers/plans/` or anywhere in the repo. Implementation plans are tracked as GitHub issues (see [[feedback-pr-workflow]]).

**Why:** The repo uses GitHub issues as the canonical plan location, not checked-in markdown.

**How to apply:** After brainstorming/writing-plans, create a GitHub issue instead of saving to `docs/superpowers/plans/`. If the writing-plans skill produces a local file, delete it before committing.
