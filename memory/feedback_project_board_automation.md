---
name: Project board status automation
description: When executing plan-as-issue, move the issue to In Progress on start — Done is handled automatically by GitHub Projects on PR merge
type: feedback
originSessionId: 6870ab81-6f65-424f-bf7b-0c4ad547b550
---
For plan issues tracked on the project board (`https://github.com/orgs/sergiodelgadotech/projects/1`), only one manual status move is needed:

- **On starting implementation:** move issue → "In Progress" via GraphQL (the CI workflow does this automatically when a PR is opened, but do it immediately when starting work on a branch without a PR yet).
- **On PR merge:** GitHub Projects' built-in "Pull request merged" automation moves the item to "Done" automatically. Do NOT do this manually.
- **Issue closure:** handled automatically via `Closes #N` in the PR body.

**Why:** The project board has a native "Pull request merged" workflow automation enabled. Manually setting Done is redundant and was corrected on 2026-06-22.

**How to apply:** Use the GraphQL sequence below only for the In Progress move at the start of work.
