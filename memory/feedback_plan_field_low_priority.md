---
name: plan-field-low-priority
description: "User doesn't care about the project board's \"Plan\" single-select field — don't ask which Plan N to use, don't burn cycles picking one"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: bbbcd3ae-2407-41a9-8d43-0e18d32789fa
---

The user doesn't care about the `Plan` field on project board <https://github.com/orgs/sergiodelgadotech/projects/1>. When creating GitHub issues that go on the board, add them to the board if needed but don't fuss over assigning a Plan-N value.

**Why:** User said directly: "i'm not very interested in the plan field, to be honest" after I assigned Plan 0 to three freshly created tickets and reported it as if it mattered. The `Plan` field was apparently set up but isn't actively curated — none of the existing 62 board items had a Plan value assigned at the time of this exchange.

**How to apply:**
- Don't ask "which Plan number?" as a clarifying question.
- When creating plan-labeled issues, the labels (`plan`, `starter-split`, `cross-repo` etc.) and `### Blocked by` task list are what matter — the board's Plan field is not.
- If the user explicitly references a Plan number later, follow their lead, but don't surface it proactively.

Related: [[project_board_automation]] covers Status-field automation, which *is* curated.
