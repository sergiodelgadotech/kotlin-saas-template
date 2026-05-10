---
name: Work on main — trunk-based development
description: This project uses trunk-based development; commit directly to main, do not create feature branches or worktrees
type: feedback
---

Both `kotlin-saas-template` and `kotlin-saas-starter` use trunk-based development. Commit directly to `main`. Do not create feature branches or git worktrees for plan execution, even when the default Claude Code/superpowers guidance says to.

**Why:** the user confirmed this on 2026-05-09 when starting Plan 1 — "in this project we use trunk-based development". Aligns with the existing project-board automation memory that notes "no PR auto-links to issue closure".

**How to apply:**
- Skip any "create a branch" / "use a worktree" steps from skills like `executing-plans` or `using-git-worktrees`.
- Plan issues that script `git push -u origin <branch-name>` should be reinterpreted as `git push` on `main`.
- For cross-repo plans, the workflow becomes: commit on `main` in both repos → publish snapshots/locals as needed → push when the user OKs → release-please opens its own release PR. There is no template/starter feature-branch PR step.
- Still confirm before pushing — local commits are fine on `main`, but `git push` is the user-visible step.
- **Releases via release-please:** the *only* PR in the loop is the one release-please opens (version bump + CHANGELOG). Don't tell the user to open a feature PR before that — pushing conventional commits straight to `main` is the input release-please needs. The user reviews/merges the release PR and the publish workflow runs from the resulting tag.
