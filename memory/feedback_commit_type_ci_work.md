---
name: commit-type-ci-work
description: "Use ci: or chore: for CI commits — never feat(ci): or fix(ci): which pollute the changelog"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 21d61f74-a32d-4bbf-9544-4afef8f27aaf
---

release-please classifies by commit type, not scope. `feat(ci):` lands in Features; `ci:` is hidden. Always use `ci:` for workflow/automation changes.

Full rule: `~/.claude/skills/think-like-me/rules/general.md` → *Commit type for CI work*
