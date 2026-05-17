---
name: Prefer @ServiceConnection over @DynamicPropertySource in tests
description: Always use @ServiceConnection when wiring Testcontainers in Spring Boot tests; never use @DynamicPropertySource
type: feedback
originSessionId: f5b33562-7b78-4c52-9f30-e7f3c6c72b51
---
Always use `@ServiceConnection` (from `spring-boot-testcontainers`) instead of `@DynamicPropertySource` when connecting Testcontainers to Spring Boot integration tests.

**Why:** User preference established when fixing issue #17. `@ServiceConnection` is cleaner and more idiomatic for Spring Boot 3.1+.

**How to apply:** When adding a container to a test class, annotate it with `@ServiceConnection(name = "redis")` (or the appropriate service name) on the `@Container` field. Add `testImplementation(libs.spring.boot.testcontainers)` to `build.gradle.kts` if not already present. Never reach for `@DynamicPropertySource` + `registry.add(...)`.
