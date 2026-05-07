# Plan 3 — Move `JobRunrConfig` to Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move JobRunr configuration from the template to the starter as `JobRunrAutoConfiguration`, providing `TenantJobFilter` and `JobSchedulerService` as `@Bean` definitions (replacing their existing `@Component`/`@Service` stereotypes). After this plan, JobRunr's own Spring Boot autoconfig handles storage and dashboard wiring, and our `TenantJobFilter` is automatically picked up by the starter's autoconfig.

**Architecture:** The starter currently annotates `TenantJobFilter` with `@Component` and `JobSchedulerService` with `@Service`, but the template's `@SpringBootApplication` only scans `org.granchi.saastemplate.**` — meaning starter beans were not actually being discovered at runtime (the template's `JobRunrConfig.kt` was the only thing wiring `TenantJobFilter`, via direct constructor parameter). We fix this systemically by adopting the canonical Spring Boot starter pattern: starter classes are POJOs, and `@AutoConfiguration` classes declare them as `@Bean`s with `@ConditionalOnMissingBean`. JobRunr's own `jobrunr-spring-boot-3-starter` then takes over storage configuration (via `DataSource` auto-discovery) and JobFilter registration (auto-discovers `JobFilter` beans).

**Tech Stack:** JobRunr 7.3.1, Spring Boot autoconfigure, JUnit 5, Strikt, Testcontainers.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 3.

**Prerequisites:** Plan 0, 1, 2 complete. The starter has `SaasStarterAutoConfiguration`, `SaasStarterProperties` (with `session` group), `SessionAutoConfiguration`, and the `AutoConfiguration.imports` file. The template depends on `kotlin-saas-starter:0.3.0`.

**Cross-repo:** Tasks 1-6 in `kotlin-saas-starter`. Tasks 7-9 in `kotlin-saas-template`. Task 10 finalizes the release as `0.4.0`.

**Note on the bigger pattern:** This plan establishes the convention for the rest of the migrations. Plans 5 (interceptors), 6 (organization service), and 7 (billing service) will each:

1. Declare relevant starter classes as `@Bean` in their autoconfig.
2. Remove the now-redundant `@Component`/`@Service` annotations from the starter source.
3. Verify with a template-side smoke test that the bean is discovered and works.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/JobRunrAutoConfiguration.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/JobRunrAutoConfigurationTest.kt`

**Modified in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/jobs/TenantJobFilter.kt` — drop `@Component`
- `src/main/kotlin/org/granchi/saasstarter/jobs/JobSchedulerService.kt` — drop `@Service`
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt` — add `Jobs` group
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — add the new FQCN
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt` — assert new entry
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt` — assert `jobs.enabled` binding

**Deleted in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/config/JobRunrConfig.kt`

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/JobRunrAutoConfigSmokeTest.kt`

**Modified in `kotlin-saas-template`:**
- `gradle/libs.versions.toml` — bump `kotlin-saas-starter` to `0.4.0-SNAPSHOT` then `0.4.0`

---

### Task 1: Add `jobs` group to `SaasStarterProperties` (failing test first)

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-starter`.

**Files:**
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`

- [ ] **Step 1: Add a failing test for the `jobs.enabled` binding**

In `SaasStarterAutoConfigurationTest.kt`, add a new test method:

```kotlin
    @Test
    fun `jobs enabled defaults to true and binds via saasstarter jobs enabled`() {
        contextRunner.run { context ->
            val props = context.getBean(SaasStarterProperties::class.java)
            expectThat(props.jobs.enabled).isTrue()
        }

        contextRunner
            .withPropertyValues("saasstarter.jobs.enabled=false")
            .run { context ->
                val props = context.getBean(SaasStarterProperties::class.java)
                expectThat(props.jobs.enabled).isFalse()
            }
    }
```

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: jobs`.

- [ ] **Step 3: Add the `Jobs` data class**

Update `SaasStarterProperties.kt`:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "saasstarter")
data class SaasStarterProperties(
    val enabled: Boolean = true,
    val session: Session = Session(),
    val jobs: Jobs = Jobs(),
) {
    data class Session(
        val enabled: Boolean = true,
    )

    data class Jobs(
        /**
         * Master switch for [JobRunrAutoConfiguration]. When false, the starter
         * does not register [TenantJobFilter] or [JobSchedulerService] as beans;
         * the consuming application may wire its own.
         */
        val enabled: Boolean = true,
    )
}
```

- [ ] **Step 4: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL. All five test methods pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add jobs group to SaasStarterProperties

saasstarter.jobs.enabled (default true) is the kill switch for the upcoming
JobRunrAutoConfiguration.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Drop `@Component` from `TenantJobFilter` and `@Service` from `JobSchedulerService`

These annotations were never honored at runtime by consuming apps (whose `@ComponentScan` doesn't reach the starter package), so removing them is safe — the autoconfig will declare both as `@Bean`s in Task 4.

**Files:**
- Modify: `src/main/kotlin/org/granchi/saasstarter/jobs/TenantJobFilter.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/jobs/JobSchedulerService.kt`

- [ ] **Step 1: Remove `@Component` import and annotation from `TenantJobFilter.kt`**

Edit the file:
- Delete the import line: `import org.springframework.stereotype.Component`
- Delete the `@Component` annotation on the class.

The class becomes:

```kotlin
package org.granchi.saasstarter.jobs

import org.granchi.saasstarter.tenant.TenantContext
import org.jobrunr.jobs.filters.JobClientFilter
import org.jobrunr.jobs.filters.JobServerFilter
import org.jobrunr.jobs.Job
// (no Spring imports — pure POJO)
import java.util.UUID

/**
 * Jobrunr filter that stores the current tenant ID in the job's
 * metadata before enqueueing, and restores it when the job runs.
 */
class TenantJobFilter : JobClientFilter, JobServerFilter {
    // ... (rest of the class unchanged)
}
```

- [ ] **Step 2: Remove `@Service` import and annotation from `JobSchedulerService.kt`**

Edit the file:
- Delete the import line: `import org.springframework.stereotype.Service`
- Delete the `@Service` annotation on the class.

The class becomes a pure POJO that takes `JobScheduler` as a constructor parameter.

- [ ] **Step 3: Verify the project still compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. (No tests should break — these classes had no unit tests previously, and the autoconfig wiring is added in Task 4.)

- [ ] **Step 4: Don't commit yet** — Tasks 3-5 finish the migration; commit after Task 5 to keep the history coherent.

---

### Task 3: Write the failing test for `JobRunrAutoConfiguration`

**Files:**
- Create: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/JobRunrAutoConfigurationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.granchi.saasstarter.jobs.JobSchedulerService
import org.granchi.saasstarter.jobs.TenantJobFilter
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

class JobRunrAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JobRunrAutoConfiguration::class.java))
        // Provide a mock JobScheduler so JobSchedulerService can be constructed
        .withBean(JobScheduler::class.java, { org.mockito.kotlin.mock() })

    @Test
    fun `tenant job filter bean is registered when enabled`() {
        contextRunner.run { context ->
            val filters = context.getBeansOfType(TenantJobFilter::class.java)
            expectThat(filters).hasSize(1)
        }
    }

    @Test
    fun `job scheduler service bean is registered when enabled`() {
        contextRunner.run { context ->
            val services = context.getBeansOfType(JobSchedulerService::class.java)
            expectThat(services).hasSize(1)
        }
    }

    @Test
    fun `autoconfig is skipped when jobs enabled is false`() {
        contextRunner
            .withPropertyValues("saasstarter.jobs.enabled=false")
            .run { context ->
                val filters = context.getBeansOfType(TenantJobFilter::class.java)
                expectThat(filters).hasSize(0)
            }
    }

    @Test
    fun `tenant job filter is overridable by user-defined bean`() {
        contextRunner
            .withBean("userFilter", TenantJobFilter::class.java, { TenantJobFilter() })
            .run { context ->
                val filters = context.getBeansOfType(TenantJobFilter::class.java)
                expectThat(filters).hasSize(1)
                expectThat(filters.keys.first()).isNotNull()
            }
    }
}
```

If `org.mockito.kotlin.mock()` is not available in the starter's testImplementation, replace it with a manual stub:

```kotlin
.withBean(JobScheduler::class.java, { object : JobScheduler { /* no-op */ } as JobScheduler })
```

(JobScheduler is an interface; in older JobRunr versions it may be a class — check before substituting. The simplest fallback is to mock with mockito-kotlin if available.)

- [ ] **Step 2: Add `mockito-kotlin` to test dependencies if not already present**

Check `gradle/libs.versions.toml`:

```bash
grep -i "mockito" gradle/libs.versions.toml
```

If absent, add to the `[versions]` section:

```toml
mockito-kotlin    = "5.4.0"
```

Add to the `[libraries]` section:

```toml
mockito-kotlin                = { module = "org.mockito.kotlin:mockito-kotlin", version.ref = "mockito-kotlin" }
```

Add to `build.gradle.kts` test dependencies:

```kotlin
testImplementation(libs.mockito.kotlin)
```

- [ ] **Step 3: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.JobRunrAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: JobRunrAutoConfiguration`.

---

### Task 4: Implement `JobRunrAutoConfiguration`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/JobRunrAutoConfiguration.kt`

- [ ] **Step 1: Write the autoconfig**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.granchi.saasstarter.jobs.JobSchedulerService
import org.granchi.saasstarter.jobs.TenantJobFilter
import org.jobrunr.scheduling.JobScheduler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * Registers JobRunr-related beans:
 * - [TenantJobFilter] is auto-picked-up by JobRunr's Spring Boot starter as a
 *   JobFilter, propagating tenant context across job lifecycle events.
 * - [JobSchedulerService] is the public API for scheduling jobs.
 *
 * Storage provider, background job server, and dashboard are configured by
 * jobrunr-spring-boot-3-starter using the standard `org.jobrunr.*` properties.
 *
 * Disabled if `saasstarter.jobs.enabled=false`.
 */
@AutoConfiguration
@ConditionalOnClass(JobScheduler::class)
@ConditionalOnProperty(
    prefix = "saasstarter.jobs",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(SaasStarterProperties::class)
class JobRunrAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun tenantJobFilter(): TenantJobFilter = TenantJobFilter()

    @Bean
    @ConditionalOnMissingBean
    fun jobSchedulerService(jobScheduler: JobScheduler): JobSchedulerService =
        JobSchedulerService(jobScheduler)
}
```

- [ ] **Step 2: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.JobRunrAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL. All four tests pass.

---

### Task 5: Register `JobRunrAutoConfiguration` in the imports file and commit

**Files:**
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`

- [ ] **Step 1: Append the new FQCN**

The file should now be:

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
org.granchi.saasstarter.autoconfigure.JobRunrAutoConfiguration
```

- [ ] **Step 2: Add a test asserting the new entry**

In `AutoConfigurationImportsTest.kt`:

```kotlin
    @Test
    fun `imports file lists JobRunrAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()
        expectThat(resource!!.readText())
            .contains("org.granchi.saasstarter.autoconfigure.JobRunrAutoConfiguration")
    }
```

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit Tasks 2-5 together**

```bash
git add src/main/kotlin/org/granchi/saasstarter/jobs/TenantJobFilter.kt \
        src/main/kotlin/org/granchi/saasstarter/jobs/JobSchedulerService.kt \
        src/main/kotlin/org/granchi/saasstarter/autoconfigure/JobRunrAutoConfiguration.kt \
        src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/JobRunrAutoConfigurationTest.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt \
        gradle/libs.versions.toml \
        build.gradle.kts
git commit -m "$(cat <<'EOF'
feat: add JobRunrAutoConfiguration with tenant filter and scheduler beans

- JobRunrAutoConfiguration declares TenantJobFilter and JobSchedulerService
  as @Bean, picked up by jobrunr-spring-boot-3-starter automatically.
- Drop @Component from TenantJobFilter and @Service from JobSchedulerService;
  these stereotypes were never honored at runtime by consumers (their
  @ComponentScan doesn't reach the starter package). The autoconfig is now
  the single source of bean creation.
- Adds saasstarter.jobs.enabled kill switch.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Publish `0.4.0-SNAPSHOT` to mavenLocal

- [ ] **Step 1: Publish**

```bash
./gradlew publishToMavenLocal -Pversion=0.4.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm the JAR contains the updated imports file**

```bash
unzip -p ~/.m2/repository/org/granchi/kotlin-saas-starter/0.4.0-SNAPSHOT/kotlin-saas-starter-0.4.0-SNAPSHOT.jar \
        META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Expected: three FQCN lines, last one being `JobRunrAutoConfiguration`.

---

### Task 7: Bump starter version and remove `JobRunrConfig.kt` in template

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-template`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/config/JobRunrConfig.kt`

- [ ] **Step 1: Bump the starter version**

In `gradle/libs.versions.toml`:

```toml
kotlin-saas-starter = "0.4.0-SNAPSHOT"
```

- [ ] **Step 2: Refresh dependencies**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: line containing `org.granchi:kotlin-saas-starter:0.4.0-SNAPSHOT`.

- [ ] **Step 3: Delete `JobRunrConfig.kt`**

```bash
git rm app/src/main/kotlin/org/granchi/saastemplate/config/JobRunrConfig.kt
```

- [ ] **Step 4: Verify the project still compiles**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL. JobRunr's own Spring Boot starter takes over storage configuration; the starter's autoconfig provides `TenantJobFilter` and `JobSchedulerService`.

- [ ] **Step 5: Don't commit yet** — Task 8 adds the smoke test.

---

### Task 8: Add a smoke test in template confirming jobs work end-to-end

**Files:**
- Create: `app/src/test/kotlin/org/granchi/saastemplate/integration/JobRunrAutoConfigSmokeTest.kt`

This integration test verifies that:
1. `JobSchedulerService` and `TenantJobFilter` are wired as beans by the starter's autoconfig.
2. A scheduled job actually executes (proving JobRunr's storage and worker are operational).
3. Tenant context is propagated through the filter (the integration test sets a tenant ID, schedules a job that reads `TenantContext`, and asserts it sees the same ID).

- [ ] **Step 1: Write the smoke test**

```kotlin
package org.granchi.saastemplate.integration

import org.granchi.saasstarter.jobs.JobSchedulerService
import org.granchi.saasstarter.jobs.TenantJobFilter
import org.granchi.saasstarter.tenant.TenantContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Tag("integration")
@SpringBootTest
class JobRunrAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var jobSchedulerService: JobSchedulerService

    @Test
    fun `JobRunrAutoConfiguration wires TenantJobFilter and JobSchedulerService`() {
        expectThat(context.getBeansOfType(TenantJobFilter::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(JobSchedulerService::class.java)).hasSize(1)
    }

    @Test
    fun `scheduled job runs and inherits tenant context from caller`() {
        val tenantId = UUID.randomUUID()
        val recordedTenantId = ConcurrentHashMap<String, UUID?>()
        val latch = CountDownLatch(1)

        // Hand the latch + recorded value bag to the static bridge so the
        // job lambda can reach them across class loaders.
        TestJobBridge.recordedTenantId = recordedTenantId
        TestJobBridge.latch = latch

        TenantContext.set(tenantId)
        try {
            jobSchedulerService.scheduleIn(Duration.ofMillis(100)) {
                TestJobBridge.captureTenantContext()
            }
        } finally {
            TenantContext.clear()
        }

        val ranWithinTimeout = latch.await(15, TimeUnit.SECONDS)
        expectThat(ranWithinTimeout).isEqualTo(true)
        expectThat(recordedTenantId["tenant"]).isNotNull().isEqualTo(tenantId)
    }
}

/**
 * Static bridge for the job lambda. JobRunr serializes the lambda body and
 * runs it on a worker thread — capturing closures over local variables in
 * the test class is fragile, so we use a static record-and-latch.
 */
object TestJobBridge {
    @JvmStatic var recordedTenantId: ConcurrentHashMap<String, UUID?>? = null
    @JvmStatic var latch: CountDownLatch? = null

    @JvmStatic
    fun captureTenantContext() {
        recordedTenantId?.put("tenant", if (TenantContext.isPresent()) TenantContext.get() else null)
        latch?.countDown()
    }
}
```

- [ ] **Step 2: Run the smoke test**

```bash
./gradlew :app:integrationTest --tests "org.granchi.saastemplate.integration.JobRunrAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL. Both tests pass within the 15-second latch timeout.

If the second test times out, JobRunr's background job server is not running. Check `org.jobrunr.background-job-server.enabled=true` in `application.yml` (or `application-test.yml`). If the test profile disables the worker, set it explicitly via `@SpringBootTest(properties = ["org.jobrunr.background-job-server.enabled=true"])`.

- [ ] **Step 3: Run full test suite**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/kotlin/org/granchi/saastemplate/config/JobRunrConfig.kt \
        app/src/test/kotlin/org/granchi/saastemplate/integration/JobRunrAutoConfigSmokeTest.kt
git commit -m "$(cat <<'EOF'
refactor: delegate JobRunr wiring to kotlin-saas-starter 0.4.0

JobRunrConfig.kt is removed. JobRunrAutoConfiguration in the starter
provides TenantJobFilter and JobSchedulerService; jobrunr-spring-boot-3-starter
auto-configures storage from the DataSource. Smoke test verifies both bean
wiring and end-to-end tenant-context propagation through a scheduled job.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Open release PRs and finalize

- [ ] **Step 1: Push starter branch and open PR**

In `/var/home/serandel/Projects/kotlin-saas-starter`:

```bash
git push -u origin <branch-name>
gh pr create --title "feat: add JobRunrAutoConfiguration" \
             --body "$(cat <<'EOF'
## Summary

- Adds JobRunrAutoConfiguration declaring TenantJobFilter and JobSchedulerService as @Bean.
- Drops @Component / @Service from those classes (stereotypes were never honored by consuming apps' @ComponentScan).
- Adds saasstarter.jobs.enabled kill switch.

Implements Plan 3 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Test plan

- [x] JobRunrAutoConfigurationTest: bean wiring, kill switch, override
- [x] AutoConfigurationImportsTest covers the new entry
- [x] Template JobRunrAutoConfigSmokeTest verifies end-to-end tenant context propagation in a real scheduled job

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI, merge PR, merge release-please PR**

After merge, `0.4.0` is published.

- [ ] **Step 3: Bump template to released `0.4.0`**

```bash
sed -i 's/kotlin-saas-starter = "0.4.0-SNAPSHOT"/kotlin-saas-starter = "0.4.0"/' \
       gradle/libs.versions.toml
```

- [ ] **Step 4: Re-run tests against the released version**

```bash
./gradlew :app:test :app:integrationTest --refresh-dependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit and push**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
build: bump kotlin-saas-starter to 0.4.0

Replaces 0.4.0-SNAPSHOT with the released version (Plan 3: JobRunrAutoConfiguration).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

---

## Done When

- `kotlin-saas-starter` 0.4.0 is published with `JobRunrAutoConfiguration`.
- Template depends on `0.4.0`, has no `JobRunrConfig.kt`, and `JobRunrAutoConfigSmokeTest` passes (including the tenant-context propagation test).
- `./gradlew :app:test :app:integrationTest` is green in the template.
- Template's `config/` package now contains 4 files: `MemberTenantResolver`, `RedisConfig`, `SecurityConfig`, `WebMvcConfig`.
- Both repos have their commits pushed.
