# Plan 2 — Move `SessionConfig` to Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Redis-backed HTTP session configuration from the template to a `SessionAutoConfiguration` class in `kotlin-saas-starter`, so all forks get the same 24h Redis session behavior with a kill switch (`saasstarter.session.enabled`) and the option to override.

**Architecture:** The starter gains `SessionAutoConfiguration` (an `@AutoConfiguration` class that turns on `@EnableRedisHttpSession`) and extends `SaasStarterProperties` with a nested `session` group. It's gated by `@ConditionalOnClass(EnableRedisHttpSession::class)` (so it only fires if the consumer has Spring Session on the classpath), `@ConditionalOnProperty(prefix = "saasstarter.session", name = "enabled", matchIfMissing = true)` (kill switch), and `@ConditionalOnMissingBean(SessionRepository::class)` (forks override by defining their own session repository). The template deletes its `SessionConfig.kt`; the autoconfig takes over transparently.

**Tech Stack:** Spring Session Redis 3.4, Spring Boot autoconfigure, Strikt, Testcontainers.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 2.

**Prerequisites:** Plan 0 (rename), Plan 1 (autoconfig scaffolding) complete. The starter has `SaasStarterAutoConfiguration` and `SaasStarterProperties`. The template depends on `kotlin-saas-starter:0.2.0`.

**Cross-repo:** Tasks 1-5 in `kotlin-saas-starter`. Tasks 6-8 in `kotlin-saas-template`. Task 9 finalizes the release.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfiguration.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfigurationTest.kt`

**Modified in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt` — add nested `Session` group
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — add the new autoconfig FQCN
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt` — extend with a test for the nested `session.enabled` property

**Deleted in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/config/SessionConfig.kt`

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/SessionAutoConfigSmokeTest.kt`

**Modified in `kotlin-saas-template`:**
- `gradle/libs.versions.toml` — bump `kotlin-saas-starter` to `0.3.0-SNAPSHOT` then `0.3.0`

---

### Task 1: Extend `SaasStarterProperties` with a `Session` group (failing test first)

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-starter`.

**Files:**
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`

- [ ] **Step 1: Add a failing test for the nested session group**

In `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`, add a new test method to the existing class:

```kotlin
    @Test
    fun `session enabled defaults to true and binds via saasstarter session enabled`() {
        contextRunner.run { context ->
            val props = context.getBean(SaasStarterProperties::class.java)
            expectThat(props.session.enabled).isTrue()
        }

        contextRunner
            .withPropertyValues("saasstarter.session.enabled=false")
            .run { context ->
                val props = context.getBean(SaasStarterProperties::class.java)
                expectThat(props.session.enabled).isFalse()
            }
    }
```

- [ ] **Step 2: Run the test — should fail with compile error**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: session` on `props.session.enabled`. Confirms the test exercises the missing nested group.

- [ ] **Step 3: Add the nested `Session` data class**

Update `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Root configuration properties for kotlin-saas-starter. Each nested group
 * maps to a focused @AutoConfiguration class: session, jobs, cache, tenant,
 * rate-limit, billing.
 */
@ConfigurationProperties(prefix = "saasstarter")
data class SaasStarterProperties(
    val enabled: Boolean = true,
    val session: Session = Session(),
) {
    data class Session(
        /**
         * Master switch for [SessionAutoConfiguration]. When false, the starter
         * does not enable Redis HTTP session storage; the consuming application
         * may wire its own.
         */
        val enabled: Boolean = true,
    )
}
```

- [ ] **Step 4: Run the test — should pass**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL. All four test methods pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add session group to SaasStarterProperties

Nested saasstarter.session.enabled property (default true) provides the kill
switch for the upcoming SessionAutoConfiguration.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Write the failing test for `SessionAutoConfiguration`

**Files:**
- Create: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfigurationTest.kt`

- [ ] **Step 1: Write the test**

Create the file:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.session.SessionRepository
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.hasSize

class SessionAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SessionAutoConfiguration::class.java,
                // Real RedisSessionConfiguration depends on a RedisConnectionFactory
                // we don't want to spin up here. We assert on bean presence/absence
                // of the autoconfig itself, not on a fully-wired session repository.
            )
        )

    @Test
    fun `autoconfig is loaded when session enabled defaults to true`() {
        contextRunner.run { context ->
            val configs = context.getBeansOfType(SessionAutoConfiguration::class.java)
            expectThat(configs).hasSize(1)
        }
    }

    @Test
    fun `autoconfig is skipped when session enabled is explicitly false`() {
        contextRunner
            .withPropertyValues("saasstarter.session.enabled=false")
            .run { context ->
                val beanNames = context.beanDefinitionNames.toList()
                expectThat(beanNames).doesNotContain("sessionAutoConfiguration")
            }
    }

    @Test
    fun `autoconfig backs off if a SessionRepository bean already exists`() {
        contextRunner
            .withBean(
                "userDefinedSessionRepository",
                SessionRepository::class.java,
                { object : SessionRepository<org.springframework.session.MapSession> {
                    override fun createSession() = org.springframework.session.MapSession()
                    override fun save(session: org.springframework.session.MapSession) {}
                    override fun findById(id: String) = null
                    override fun deleteById(id: String) {}
                } }
            )
            .run { context ->
                val configs = context.getBeansOfType(SessionAutoConfiguration::class.java)
                expectThat(configs).hasSize(0)
            }
    }
}
```

- [ ] **Step 2: Run the test — should fail with compile error**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SessionAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: SessionAutoConfiguration`.

---

### Task 3: Implement `SessionAutoConfiguration`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfiguration.kt`

- [ ] **Step 1: Write the autoconfig**

Create `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfiguration.kt`:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.session.SessionRepository
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession

/**
 * Stores HTTP sessions in Redis with a 24h inactivity timeout, allowing
 * horizontal scaling without sticky sessions.
 *
 * Disabled if either:
 * - `saasstarter.session.enabled=false`, or
 * - the consumer defines its own [SessionRepository] bean.
 */
@AutoConfiguration
@ConditionalOnClass(EnableRedisHttpSession::class)
@ConditionalOnProperty(
    prefix = "saasstarter.session",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnMissingBean(SessionRepository::class)
@EnableConfigurationProperties(SaasStarterProperties::class)
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 86400)
class SessionAutoConfiguration
```

The 24h timeout (`86400` seconds) matches the template's previous `SessionConfig`. A future plan can promote this to a property (`saasstarter.session.timeout`) if needed; for now it's a constant since `@EnableRedisHttpSession` requires one.

- [ ] **Step 2: Run the test — should pass**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SessionAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL. All three tests pass.

If "autoconfig backs off if a SessionRepository bean already exists" still loads `SessionAutoConfiguration`, the `@ConditionalOnMissingBean(SessionRepository::class)` is on the wrong target; verify the annotation is at the class level.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfiguration.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/SessionAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add SessionAutoConfiguration for Redis HTTP sessions

Ships the equivalent of @EnableRedisHttpSession with a 24h timeout, gated by
saasstarter.session.enabled (default true) and backing off if the consumer
defines its own SessionRepository.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Register `SessionAutoConfiguration` in the imports file

**Files:**
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Add the new FQCN**

Append a line so the file contains:

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
```

- [ ] **Step 2: Update the imports test to assert the new entry**

In `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`, add:

```kotlin
    @Test
    fun `imports file lists SessionAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()
        expectThat(resource!!.readText())
            .contains("org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration")
    }
```

- [ ] **Step 3: Run all tests**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt
git commit -m "$(cat <<'EOF'
feat: register SessionAutoConfiguration via AutoConfiguration.imports

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Publish `0.3.0-SNAPSHOT` to mavenLocal

**Files:** none modified.

- [ ] **Step 1: Publish**

```bash
./gradlew publishToMavenLocal -Pversion=0.3.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL. Artifact lives at `~/.m2/repository/org/granchi/kotlin-saas-starter/0.3.0-SNAPSHOT/`.

- [ ] **Step 2: Sanity-check the JAR**

```bash
unzip -p ~/.m2/repository/org/granchi/kotlin-saas-starter/0.3.0-SNAPSHOT/kotlin-saas-starter-0.3.0-SNAPSHOT.jar \
        META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Expected output:

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
```

---

### Task 6: Bump starter version and remove `SessionConfig.kt` in template

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-template`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/config/SessionConfig.kt`

- [ ] **Step 1: Bump the starter version**

In `gradle/libs.versions.toml`:

```toml
kotlin-saas-starter = "0.3.0-SNAPSHOT"
```

(Assumes `mavenLocal()` is in `settings.gradle.kts` from Plan 1 Task 7. If not, add it back.)

- [ ] **Step 2: Refresh dependencies**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: a line containing `org.granchi:kotlin-saas-starter:0.3.0-SNAPSHOT`.

- [ ] **Step 3: Delete `SessionConfig.kt`**

```bash
git rm app/src/main/kotlin/org/granchi/saastemplate/config/SessionConfig.kt
```

- [ ] **Step 4: Verify the project still compiles**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL. The starter's `SessionAutoConfiguration` now provides the same behavior the template's deleted file did.

- [ ] **Step 5: Don't commit yet** — Task 7 adds a smoke test to commit alongside.

---

### Task 7: Add a smoke test in template confirming session storage still works

**Files:**
- Create: `app/src/test/kotlin/org/granchi/saastemplate/integration/SessionAutoConfigSmokeTest.kt`

- [ ] **Step 1: Write the smoke test**

Create the file:

```kotlin
package org.granchi.saastemplate.integration

import org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.session.SessionRepository
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

@Tag("integration")
@SpringBootTest
class SessionAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `SessionAutoConfiguration is discovered and active`() {
        val configs = context.getBeansOfType(SessionAutoConfiguration::class.java)
        expectThat(configs).hasSize(1)
    }

    @Test
    fun `Spring Session provides a SessionRepository bean`() {
        val repos = context.getBeansOfType(SessionRepository::class.java)
        expectThat(repos.values.firstOrNull()).isNotNull()
    }
}
```

- [ ] **Step 2: Run the smoke test (requires Redis available — Testcontainers or local Docker)**

```bash
./gradlew :app:integrationTest --tests "org.granchi.saastemplate.integration.SessionAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL. Both tests pass.

- [ ] **Step 3: Run the full integration test suite to confirm no regressions**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL. `TenantIsolationTest` and others continue to pass — the deleted `SessionConfig.kt` was redundant once the starter took over.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/kotlin/org/granchi/saastemplate/config/SessionConfig.kt \
        app/src/test/kotlin/org/granchi/saastemplate/integration/SessionAutoConfigSmokeTest.kt
git commit -m "$(cat <<'EOF'
refactor: delegate Redis HTTP session config to kotlin-saas-starter 0.3.0

SessionConfig.kt is removed; SessionAutoConfiguration in the starter provides
the same @EnableRedisHttpSession with 24h timeout. SessionAutoConfigSmokeTest
verifies the autoconfig is discovered and a SessionRepository is wired.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

(Note: `git add` of the deleted file finalizes the deletion staged via `git rm` in Task 6.)

---

### Task 8: Open release PRs and finalize

- [ ] **Step 1: Push the starter branch and open PR**

In `/var/home/serandel/Projects/kotlin-saas-starter`:

```bash
git push -u origin <branch-name>
gh pr create --title "feat: add SessionAutoConfiguration" \
             --body "$(cat <<'EOF'
## Summary

- Adds SessionAutoConfiguration providing 24h Redis HTTP sessions.
- Extends SaasStarterProperties with a session group (saasstarter.session.enabled).
- Backs off via @ConditionalOnMissingBean(SessionRepository) so consumers can override.

Implements Plan 2 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Test plan

- [x] SessionAutoConfigurationTest verifies kill switch and override behavior
- [x] AutoConfigurationImportsTest verifies the FQCN is registered
- [x] Template SessionAutoConfigSmokeTest passes against 0.3.0-SNAPSHOT (separate PR)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI, merge PR, merge release-please PR**

After release-please opens its release PR with version 0.3.0 and merges it, tag `v0.3.0` triggers `publish` which pushes `org.granchi:kotlin-saas-starter:0.3.0` to GitHub Packages.

- [ ] **Step 3: Bump template to released `0.3.0`**

In `/var/home/serandel/Projects/kotlin-saas-template`:

```bash
sed -i 's/kotlin-saas-starter = "0.3.0-SNAPSHOT"/kotlin-saas-starter = "0.3.0"/' \
       gradle/libs.versions.toml
```

- [ ] **Step 4: Re-run tests against the released version**

```bash
./gradlew :app:test :app:integrationTest --refresh-dependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit and push template**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
build: bump kotlin-saas-starter to 0.3.0

Replaces 0.3.0-SNAPSHOT with the released version (Plan 2: SessionAutoConfiguration).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

---

## Done When

- `kotlin-saas-starter` 0.3.0 is published to GitHub Packages with `SessionAutoConfiguration`.
- Template depends on `0.3.0`, has no `SessionConfig.kt`, and `SessionAutoConfigSmokeTest` passes.
- `./gradlew :app:test :app:integrationTest` is green in the template.
- The template's `config/` package now contains 5 files (was 6): `MemberTenantResolver`, `JobRunrConfig`, `RedisConfig`, `SecurityConfig`, `WebMvcConfig`.
- Both repos have their commits pushed.
