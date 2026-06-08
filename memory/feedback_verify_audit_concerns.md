---
name: verify-audit-concerns-before-reporting
description: "When summarizing audit/exploration agent findings about a library's quality, verify each claimed bug/risk against current code AND open-issue status BEFORE passing it upstream — never copy \"concerns\" lists verbatim from agent reports"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: e64d7818-6057-4afe-a993-f591f71c1901
---

When a subagent (Explore/audit) returns a list of "sharp edges", "concerns", "TODOs", "risks", or "bugs", verify each one against:

1. **Current code** — read the actual file; the agent may have summarized a stale read or misinterpreted intent.
2. **Open-issue status** — `gh issue view <n>` to confirm whether it's still open, closed, or being actively worked.
3. **Severity in the user's actual stack** — a concern that matters for SPAs/REST may be a non-issue for Thymeleaf+HTMX, and vice versa. Don't report severity in the abstract.

**Why:** during the 2026-06-01 gap analysis of `kotlin-saas-starter`, an Explore agent surfaced six "starter-side bugs/limitations" as risks. After verification, the rate-limiter bug was already closed (#22), the SB4 upgrade was already done (#16/#13), the view-only error handler was actually the right output for a Thymeleaf+HTMX consumer, and the remaining items were minor metadata/cosmetic fixes. Reporting the unverified list as-is made the user worry that a library they're about to depend on was unsafe to use, when in fact it was production-ready for their stack. The agent's audit was honest about what it saw in code, but I propagated it as concerns without weighing each against (a) issue state and (b) the user's actual stack.

**How to apply:** before relaying any audit's "concerns" / "risks" / "TODOs" section to the user, for each item: (1) read the cited file, (2) check the cited issue's current state, (3) classify risk as "blocking / low / cosmetic / none" *in the context of this user's stack*, and only report what survives that filter. If 4 of 6 items evaporate after verification, say so — don't preserve them out of caution. Aligns with [[guardrails-from-CLAUDE-md]] ("Verify external dependency versions actually exist before relying on them in plans").
