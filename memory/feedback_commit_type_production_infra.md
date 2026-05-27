---
name: feedback_commit_type_production_infra
description: "Use perf: for runtime infrastructure swaps motivated by performance (e.g. switching embedded servers)"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: e56bb9fa-24a0-4453-8727-0560bf4368a8
---

For changes that swap a runtime dependency for a lighter/faster alternative (e.g. Tomcat → Jetty), prefer `perf:` over `feat:` or `chore:`. `perf:` communicates the motivation (lower memory, faster startup) without polluting the feature changelog.

**Why:** `feat:` implies user-visible capability; `chore:` implies no runtime impact. `perf:` is the most accurate label when the goal is a performance improvement with no new functionality.

**How to apply:** Ask: is this swap motivated by performance? If yes and it has no user-visible behavior change → `perf:`.

Related: [[feedback_commit_type_ci_work]]
