# Plan 1 — Autoconfiguration Scaffolding in Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Spring Boot autoconfiguration infrastructure to `kotlin-saas-starter` so subsequent plans can move beans into the starter as `@AutoConfiguration` classes that template apps pick up automatically.

**Architecture:** Three artifacts are added to the starter: (1) a top-level `SaasStarterAutoConfiguration` class annotated with `@AutoConfiguration`, (2) a `SaasStarterProperties` class annotated with `@ConfigurationProperties(prefix = "saasstarter")` for property binding, and (3) a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file listing the autoconfig FQCN so Spring Boot discovers it. Subsequent plans extend `SaasStarterProperties` with nested groups (session, jobs, cache, tenant, rate-limit, billing) and add new autoconfig classes alongside `SaasStarterAutoConfiguration`.

**Tech Stack:** Kotlin, Spring Boot 3.4 autoconfigure APIs, Strikt for assertions, JUnit 5.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 1.

**Prerequisites:** Plan 0 complete (template renamed to `kotlin-saas-template`, package `org.granchi.saastemplate`).

**Cross-repo:** Tasks 1-6 happen in `kotlin-saas-starter`. Tasks 7-8 happen in `kotlin-saas-template`. Both finish with pushed commits.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfiguration.kt` — top-level `@AutoConfiguration` umbrella
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt` — `@ConfigurationProperties(prefix = "saasstarter")` root
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — autoconfig discovery
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt` — `ApplicationContextRunner` tests
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt` — verifies imports file references the autoconfig FQCN

**Modified in `kotlin-saas-starter`:**
- `gradle/libs.versions.toml` — add `spring-boot-autoconfigure` library
- `build.gradle.kts` — depend on it (compileOnly + testImplementation)

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/StarterAutoConfigSmokeTest.kt` — `@SpringBootTest` proving the autoconfig is discovered at app boot

**Modified in `kotlin-saas-template`:**
- `gradle/libs.versions.toml` — bump `kotlin-saas-starter` to `0.2.0`

---

### Task 1: Add `spring-boot-autoconfigure` dependency to starter

**Files:**
- Modify: `kotlin-saas-starter/gradle/libs.versions.toml`
- Modify: `kotlin-saas-starter/build.gradle.kts`

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-starter`.

- [ ] **Step 1: Add the library entry to `libs.versions.toml`**

In `gradle/libs.versions.toml`, add a new line in the `[libraries]` section (alphabetical placement near other `spring-boot-*` entries):

```toml
spring-boot-autoconfigure     = { module = "org.springframework.boot:spring-boot-autoconfigure" }
```

The Spring Boot BOM imported in `build.gradle.kts` (`spring-boot-dependencies:3.4.1`) supplies the version, so no `version.ref` is needed.

- [ ] **Step 2: Reference the new library in `build.gradle.kts`**

In the `dependencies { ... }` block of `build.gradle.kts`, add a `compileOnly` reference next to the other Spring Boot ones (around line 28):

```kotlin
compileOnly(libs.spring.boot.autoconfigure)
```

And add a matching `testImplementation` line in the test dependencies section:

```kotlin
testImplementation(libs.spring.boot.autoconfigure)
```

- [ ] **Step 3: Verify Gradle resolves the dependency**

```bash
./gradlew dependencies --configuration compileClasspath | grep spring-boot-autoconfigure
```

Expected: at least one line containing `org.springframework.boot:spring-boot-autoconfigure:3.4.1`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "$(cat <<'EOF'
build: add spring-boot-autoconfigure dependency

Required for @AutoConfiguration classes shipped from the starter.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Write the failing test for `SaasStarterAutoConfiguration`

**Files:**
- Create: `kotlin-saas-starter/src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`

- [ ] **Step 1: Create the test directory and file**

```bash
mkdir -p src/test/kotlin/org/granchi/saasstarter/autoconfigure
```

- [ ] **Step 2: Write the test**

Create `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt` with:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class SaasStarterAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SaasStarterAutoConfiguration::class.java))

    @Test
    fun `autoconfig class loads as a bean`() {
        contextRunner.run { context ->
            expectThat(context.containsBean("saasStarterAutoConfiguration")).isTrue()
        }
    }

    @Test
    fun `properties bean is registered with default enabled=true`() {
        contextRunner.run { context ->
            val props = context.getBean(SaasStarterProperties::class.java)
            expectThat(props.enabled).isTrue()
        }
    }

    @Test
    fun `enabled property can be overridden via configuration`() {
        contextRunner
            .withPropertyValues("saasstarter.enabled=false")
            .run { context ->
                val props = context.getBean(SaasStarterProperties::class.java)
                expectThat(props.enabled).isFalse()
            }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD FAILED with compilation errors — `Unresolved reference: SaasStarterAutoConfiguration` and `Unresolved reference: SaasStarterProperties`. This confirms the test runs against missing classes — the next two tasks add them.

---

### Task 3: Implement `SaasStarterProperties`

**Files:**
- Create: `kotlin-saas-starter/src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`

- [ ] **Step 1: Create the main source directory for autoconfigure**

```bash
mkdir -p src/main/kotlin/org/granchi/saasstarter/autoconfigure
```

- [ ] **Step 2: Write `SaasStarterProperties.kt`**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Root configuration properties for kotlin-saas-starter. Subsequent plans
 * extend this with nested groups (session, jobs, cache, tenant, rate-limit, billing).
 */
@ConfigurationProperties(prefix = "saasstarter")
data class SaasStarterProperties(
    val enabled: Boolean = true,
)
```

The `enabled` flag is a kill switch for the entire starter. Subsequent autoconfigs use it (and their own per-feature flags) via `@ConditionalOnProperty`.

- [ ] **Step 3: Commit (intermediate, will be combined later if preferred)**

Skip standalone commit — Tasks 3 + 4 land together since the test in Task 2 needs both.

---

### Task 4: Implement `SaasStarterAutoConfiguration`

**Files:**
- Create: `kotlin-saas-starter/src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfiguration.kt`

- [ ] **Step 1: Write `SaasStarterAutoConfiguration.kt`**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Top-level autoconfiguration for kotlin-saas-starter. Subsequent plans add
 * focused autoconfig classes (e.g. SessionAutoConfiguration) declared in the
 * AutoConfiguration.imports file alongside this one.
 */
@AutoConfiguration
@EnableConfigurationProperties(SaasStarterProperties::class)
class SaasStarterAutoConfiguration
```

- [ ] **Step 2: Run the test from Task 2**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL. All three test methods pass.

If the `containsBean("saasStarterAutoConfiguration")` assertion fails, Spring may have used a different bean name. In that case, query by type instead:

```kotlin
expectThat(context.getBeansOfType(SaasStarterAutoConfiguration::class.java)).hasSize(1)
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/autoconfigure/ \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add SaasStarterAutoConfiguration scaffolding

Adds the umbrella @AutoConfiguration class and @ConfigurationProperties root
that subsequent migrations extend. Includes tests using ApplicationContextRunner
to verify the autoconfig loads and properties bind under the saasstarter.* prefix.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

The `feat:` prefix is intentional — release-please will treat this as a minor bump (0.1.0 → 0.2.0).

---

### Task 5: Create the `AutoConfiguration.imports` file

**Files:**
- Create: `kotlin-saas-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `kotlin-saas-starter/src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`

The `.imports` file is how Spring Boot 2.7+ discovers autoconfigs in libraries (replacing `spring.factories`). We write a test asserting the file exists and lists the autoconfig FQCN so any future renames are caught at test time.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isNotNull

class AutoConfigurationImportsTest {

    @Test
    fun `imports file lists SaasStarterAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()

        val content = resource!!.readText()
        expectThat(content)
            .contains("org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.AutoConfigurationImportsTest"
```

Expected: FAILED. The error is from `expectThat(resource).isNotNull()` because the file doesn't exist yet.

- [ ] **Step 3: Create the imports directory and file**

```bash
mkdir -p src/main/resources/META-INF/spring
```

Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` with exactly this content (one FQCN per line, newline at end):

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.AutoConfigurationImportsTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full test suite to confirm nothing else broke**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. All previous tests still pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/META-INF/spring/ \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt
git commit -m "$(cat <<'EOF'
feat: register SaasStarterAutoConfiguration via AutoConfiguration.imports

Spring Boot 2.7+ discovers library autoconfigs through this file. Adding it
makes the starter's autoconfig get loaded automatically by any consuming
@SpringBootApplication.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Publish 0.2.0-SNAPSHOT to mavenLocal for template testing

**Files:** none modified — just a publish.

We don't release 0.2.0 yet. The template's smoke test (Tasks 7-8) needs to validate against an in-flight starter version. Publishing a `0.2.0-SNAPSHOT` to `~/.m2` lets the template resolve via `mavenLocal()` for now.

- [ ] **Step 1: Publish to mavenLocal**

```bash
./gradlew publishToMavenLocal -Pversion=0.2.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL. The artifact `org.granchi:kotlin-saas-starter:0.2.0-SNAPSHOT` is now in `~/.m2/repository/org/granchi/kotlin-saas-starter/0.2.0-SNAPSHOT/`.

- [ ] **Step 2: Verify the artifact landed**

```bash
ls ~/.m2/repository/org/granchi/kotlin-saas-starter/0.2.0-SNAPSHOT/
```

Expected: at least `kotlin-saas-starter-0.2.0-SNAPSHOT.jar` and `kotlin-saas-starter-0.2.0-SNAPSHOT.pom`.

- [ ] **Step 3: Inspect the JAR contains the imports file**

```bash
unzip -l ~/.m2/repository/org/granchi/kotlin-saas-starter/0.2.0-SNAPSHOT/kotlin-saas-starter-0.2.0-SNAPSHOT.jar | grep AutoConfiguration.imports
```

Expected: a line listing `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

No commit needed — this task is a publish action with no source changes.

---

### Task 7: Add `mavenLocal()` and bump starter version in template

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-template` (post Plan 0).

**Files:**
- Modify: `kotlin-saas-template/settings.gradle.kts` — add `mavenLocal()` to repositories
- Modify: `kotlin-saas-template/gradle/libs.versions.toml` — bump `kotlin-saas-starter` to `0.2.0-SNAPSHOT`

- [ ] **Step 1: Add `mavenLocal()` to template's repository list**

In `settings.gradle.kts`, the `dependencyResolutionManagement.repositories { ... }` block currently lists `mavenCentral()` and the GitHub Packages maven URL. Add `mavenLocal()` as the first entry so it's checked first:

```kotlin
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
        // kotlin-saas-starter is published to GitHub Packages
        maven {
            url = uri("https://maven.pkg.github.com/granchi/kotlin-saas-starter")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    ...
}
```

- [ ] **Step 2: Bump the starter version**

Find the `kotlin-saas-starter` version in `gradle/libs.versions.toml` (under `[versions]`) and change it to `0.2.0-SNAPSHOT`:

```toml
kotlin-saas-starter = "0.2.0-SNAPSHOT"
```

- [ ] **Step 3: Refresh dependencies and verify resolution**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: at least one line containing `org.granchi:kotlin-saas-starter:0.2.0-SNAPSHOT`.

- [ ] **Step 4: Commit (intermediate, no test yet)**

We hold the commit until Task 8 adds a smoke test, so the version bump and the test that exercises it land together. No commit yet.

---

### Task 8: Add a smoke test in template asserting the autoconfig is discovered

**Files:**
- Create: `kotlin-saas-template/app/src/test/kotlin/org/granchi/saastemplate/integration/StarterAutoConfigSmokeTest.kt`

- [ ] **Step 1: Locate or create the integration test directory**

```bash
ls app/src/test/kotlin/org/granchi/saastemplate/integration/
```

Expected: directory exists with `TenantIsolationTest.kt`. (Plan 0 moved it from `mvpsaas`.)

- [ ] **Step 2: Write the smoke test**

Create `app/src/test/kotlin/org/granchi/saastemplate/integration/StarterAutoConfigSmokeTest.kt`:

```kotlin
package org.granchi.saastemplate.integration

import org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
import org.granchi.saasstarter.autoconfigure.SaasStarterProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

@SpringBootTest
class StarterAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var properties: SaasStarterProperties

    @Test
    fun `SaasStarterAutoConfiguration is discovered via the imports file`() {
        val beans = context.getBeansOfType(SaasStarterAutoConfiguration::class.java)
        expectThat(beans).hasSize(1)
    }

    @Test
    fun `SaasStarterProperties default enabled flag is true`() {
        expectThat(properties.enabled).isTrue()
    }
}
```

This test bootstraps the full Spring Boot context. If the starter's `META-INF/spring/...AutoConfiguration.imports` is missing or malformed, the autoconfig would not be discovered and the first assertion would fail.

- [ ] **Step 3: Run the smoke test**

```bash
./gradlew :app:test --tests "org.granchi.saastemplate.integration.StarterAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL. Both tests pass.

- [ ] **Step 4: Run the full test suite to confirm no regressions**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts \
        gradle/libs.versions.toml \
        app/src/test/kotlin/org/granchi/saastemplate/integration/StarterAutoConfigSmokeTest.kt
git commit -m "$(cat <<'EOF'
test: smoke-test starter autoconfig discovery

Bumps kotlin-saas-starter to 0.2.0-SNAPSHOT (resolved from mavenLocal),
adds @SpringBootTest asserting SaasStarterAutoConfiguration is loaded
via AutoConfiguration.imports.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Open release PRs and finalize

**Cross-repo coordination.** The starter side merges first so release-please can publish a real `0.2.0` to GitHub Packages; then the template's `0.2.0-SNAPSHOT` is replaced with `0.2.0`.

- [ ] **Step 1: Push the starter branch and open PR**

```bash
cd /var/home/serandel/Projects/kotlin-saas-starter
git push -u origin <branch-name>
gh pr create --title "feat: add SaasStarterAutoConfiguration scaffolding" \
             --body "$(cat <<'EOF'
## Summary

- Adds SaasStarterAutoConfiguration umbrella + SaasStarterProperties root for the saasstarter.* prefix.
- Adds the META-INF/spring/AutoConfiguration.imports file so consuming Spring Boot apps discover the autoconfig.
- Lays the rails for plans 2-7 to add focused autoconfig classes.

Implements Plan 1 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Test plan

- [x] ApplicationContextRunner loads the autoconfig
- [x] SaasStarterProperties bean binds under saasstarter.*
- [x] AutoConfiguration.imports file lists the autoconfig FQCN
- [x] Template smoke test passes with 0.2.0-SNAPSHOT (separate PR)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI to pass and merge the starter PR**

Manual / interactive. After merging:
- release-please opens a release PR with version 0.2.0 and CHANGELOG entries.
- Merging the release PR creates tag `v0.2.0` and triggers the `publish` job which pushes `0.2.0` to GitHub Packages.

- [ ] **Step 3: In the template, swap `0.2.0-SNAPSHOT` for `0.2.0`**

Once `0.2.0` is published, in `/var/home/serandel/Projects/kotlin-saas-template`:

```bash
sed -i 's/kotlin-saas-starter = "0.2.0-SNAPSHOT"/kotlin-saas-starter = "0.2.0"/' \
       gradle/libs.versions.toml
```

- [ ] **Step 4: Re-run the template tests against the released version**

```bash
./gradlew :app:test :app:integrationTest --refresh-dependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit and push**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
build: bump kotlin-saas-starter to 0.2.0

Replaces the 0.2.0-SNAPSHOT used during local development with the
released version from GitHub Packages.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

- [ ] **Step 6: (Optional) Drop `mavenLocal()` from `settings.gradle.kts`**

If you'd rather not have `mavenLocal()` in the resolution chain between migrations, remove it now. Add it back at the start of Plan 2.

If keeping it (recommended for the duration of plans 2-7), no action needed.

---

## Done When

- `kotlin-saas-starter` 0.2.0 is published to GitHub Packages.
- `kotlin-saas-starter` has `SaasStarterAutoConfiguration`, `SaasStarterProperties`, and the `AutoConfiguration.imports` file, all covered by tests.
- `kotlin-saas-template` depends on `kotlin-saas-starter:0.2.0` and `StarterAutoConfigSmokeTest` passes against it.
- `./gradlew :app:test :app:integrationTest` is green in the template.
- Both repos have their commits pushed.
