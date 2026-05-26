---
name: feedback_run_check_at_end
description: "At the end of a ticket, run ./gradlew check (not just test and integrationTest separately)"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: facd4558-9c58-4185-a8e1-c1a6f5a1563a
---

At the end of a ticket, run the full test suite with `./gradlew check`, not just `:app:test` and `:app:integrationTest` separately.

**Why:** `check` is the conventional Gradle verification lifecycle task; running it ensures nothing was missed and is the standard CI signal.

**How to apply:** After all changes are committed, run `./gradlew check` (plus `integrationTest` if not yet wired into `check`) as the final verification step before closing a ticket.

**Note:** As of Plan 7, all test suites (`integrationTest`, `e2eTest`, `architectureTest`) are wired into `check` via `tasks.check { dependsOn(...) }` in `app/build.gradle.kts`. `./gradlew check` covers everything. [[feedback_plan_issue_checkboxes]]
