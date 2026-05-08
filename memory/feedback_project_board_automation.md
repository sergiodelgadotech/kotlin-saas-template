---
name: Project board status automation
description: When executing plan-as-issue, move the issue's project-board status automatically — In Progress on start, Done + close on user-confirmed completion
type: feedback
---
For plan issues tracked on the project board (`https://github.com/users/serandel/projects/6`), move the status field automatically — don't expect the user to do it.

- **On starting implementation:** move issue → "In Progress".
- **On user-confirmed completion** (e.g. "good", "looks good", "works"): move issue → "Done" *and* close it (`gh issue close <n> --reason completed`). For cross-repo plans, do this in both repos.

**Why:** trunk-based development means no PR auto-links to issue closure. Without this, the board drifts out of sync with reality. The user explicitly asked for this automation on 2026-05-08.

**How to apply:** Use `gh project item-edit` (or equivalent) when starting and when confirming. Combine with the existing CLAUDE.md rule that user confirmation is the commit signal — the same confirmation also closes the issue.
