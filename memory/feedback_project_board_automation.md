---
name: Project board status automation
description: When executing plan-as-issue, move the issue's project-board status automatically — In Progress on start, Done + close on user-confirmed completion
type: feedback
originSessionId: 6870ab81-6f65-424f-bf7b-0c4ad547b550
---
For plan issues tracked on the project board (`https://github.com/orgs/SergioDelgado-tech/projects/1`), move the status field automatically — don't expect the user to do it.

- **On starting implementation:** move issue → "In Progress".
- **On user-confirmed completion** (e.g. "good", "looks good", "works", "push it"): move issue → "Done" *and* close it (`gh issue close <n> --reason completed`). For cross-repo plans, do this in both repos.

**Why:** trunk-based development means no PR auto-links to issue closure. Without this, the board drifts out of sync with reality. The user explicitly asked for this automation on 2026-05-08.

**How to apply:** After committing, immediately close the issue and move the board status — do not wait for the user to ask. Use the exact command sequence below.

## Correct gh project item-edit command

`gh project item-edit` does NOT accept `--owner`. The correct sequence:

```bash
PROJECT_ID=$(gh project list --owner SergioDelgado-tech --format json | jq -r '.projects[] | select(.number == 1) | .id')
STATUS_FIELD_ID=$(gh project field-list 1 --owner SergioDelgado-tech --format json | jq -r '.fields[] | select(.name == "Status") | .id')
DONE_OPTION_ID=$(gh project field-list 1 --owner SergioDelgado-tech --format json | jq -r '.fields[] | select(.name == "Status") | .options[] | select(.name == "Done") | .id')
ITEM_ID=$(gh project item-list 1 --owner SergioDelgado-tech --format json | jq -r '.items[] | select(.content.number == <N>) | .id')
gh project item-edit --id "$ITEM_ID" --project-id "$PROJECT_ID" --field-id "$STATUS_FIELD_ID" --single-select-option-id "$DONE_OPTION_ID"
```

`--owner` is valid for `item-list` and `field-list` but NOT for `item-edit`. Only `--id`, `--project-id`, `--field-id`, and `--single-select-option-id` are used in `item-edit`.
