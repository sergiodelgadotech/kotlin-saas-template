---
name: pr-based-workflow-feature-branch-squash-merge
description: "Both repos use a PR-based workflow; each task gets a feature branch, CI gates the merge, squash merge with PR title as the commit subject"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 232f92b2-d5e0-42a5-ae70-ebe2aa2adb2a
---

Both `kotlin-saas-template` and `kotlin-saas-starter` use a **PR-based workflow**. Do not commit directly to `main` — branch protection enforces this on the remote, but a local commit on `main` is equally harmful: it poisons `git pull --rebase` and causes spurious conflicts when the user syncs after merging a PR. A Claude session did exactly this (2026-06-26), creating `9bc6f5e` directly on local `main`, which conflicted when the user ran `git pull --rebase` after merging the PR.

**Why:** trunk-based development (changed 2026-06-19) caused `main` to break regularly (CI runs after the commit lands) and parallel agents could interleave changes. Branch protection now gates the merge.

**How to apply:**
- **Never commit on local `main`** — always branch first, even for tiny fixes
- Create a feature branch per task: `git checkout -b feat/short-description`
- Commit and push, then open a PR with a **conventional-commit title** (e.g. `feat: add X`, `fix: resolve Y`)
- Branch protection requires `test` + `e2e` (template) or `test` (starter) to pass, and the branch to be up to date with `main`, before merge is allowed
- **Squash merge only** — the PR title becomes the squash commit on `main`; PR titles must be conventional commits
- Include `Closes #N` in the PR body to auto-close the plan issue on merge
- For the **starter**, PR titles are what release-please parses on `main`; use `feat!:` for breaking changes
- Don't run `sbx rm` until the PR is merged — removing the sandbox deletes the branch
- GitHub auto-deletes the head branch after merge (configured)

[[feedback_project_board_automation]]
