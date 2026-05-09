---
name: Check off plan-issue checkboxes as substeps complete
description: When executing plan-as-issue, tick the GitHub issue's task-list checkboxes as you finish substeps — don't wait for the user
type: feedback
---

When executing a plan tracked as a GitHub issue (project board <https://github.com/users/serandel/projects/6>), update the issue body's task-list checkboxes (`- [ ]` → `- [x]`) as substeps complete. Use `gh issue edit <n> --body-file <(gh issue view <n> --json body -q .body | sed ...)` or fetch-edit-replace via `gh api`.

**Why:** the user explicitly asked for it on 2026-05-08 mid-execution of Plan 0. The board's `In Progress`/`Done` status is per-issue; checkboxes give per-substep granularity so the user can see exactly where execution sits without reading the conversation.

**How to apply:**
- Tick boxes in batches at task boundaries (after committing each task), not after every individual file edit — keeps API calls reasonable.
- For multi-step substeps already prescribed in the plan, tick the substep when its verification (compile/test/grep) passes.
- Combine with the existing `In Progress`/`Done` board automation (see `feedback_project_board_automation.md`).
