---
name: feedback_local_profile_default
description: "local is the default Spring Boot profile — never add --args='--spring.profiles.active=local' to bootRun commands"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 79cfca33-91bd-45fc-ac88-e5f2f71b095d
---

Never append `--args='--spring.profiles.active=local'` to `./gradlew :app:bootRun`. The `local` profile is already the default active profile in this project.

**Why:** User has corrected this multiple times.

**How to apply:** Always write `./gradlew :app:bootRun` with no extra arguments when running the app locally.
