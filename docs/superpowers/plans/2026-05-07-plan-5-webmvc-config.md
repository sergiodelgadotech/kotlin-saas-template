# Plan 5 — Move `WebMvcConfig` Pattern to Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move interceptor registration (`TenantInterceptor`, `RateLimitInterceptor`) and the underlying beans (`RateLimiter`) from the template's `WebMvcConfig.kt` to a `WebMvcAutoConfiguration` class in the starter, with path patterns supplied via `saasstarter.tenant.*` and `saasstarter.rate-limit.*` properties.

**Architecture:** The starter gains a `WebMvcAutoConfiguration` that (1) declares `TenantInterceptor`, `RateLimitInterceptor`, and `RateLimiter` as `@Bean`s — replacing their existing `@Component`/`@Service` stereotypes, which were never honored at runtime — and (2) implements `WebMvcConfigurer` to register the interceptors on path patterns from `SaasStarterProperties.Tenant` and `SaasStarterProperties.RateLimit`. The template's application uses YAML to declare which paths get tenant resolution and which get rate limiting.

**Tech Stack:** Spring Web MVC, Spring Boot autoconfigure.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 5.

**Prerequisites:** Plans 0-4 complete. The template depends on `kotlin-saas-starter:0.5.0`.

**Cross-repo:** Tasks 1-7 in `kotlin-saas-starter`. Tasks 8-10 in `kotlin-saas-template`. Task 11 finalizes as `0.6.0`.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/WebMvcAutoConfiguration.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/WebMvcAutoConfigurationTest.kt`

**Modified in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/tenant/TenantInterceptor.kt` — drop `@Component`
- `src/main/kotlin/org/granchi/saasstarter/ratelimit/RateLimitInterceptor.kt` — drop `@Component`
- `src/main/kotlin/org/granchi/saasstarter/ratelimit/RateLimiter.kt` — drop `@Service`
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt` — add `Tenant` and `RateLimit` groups
- `src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`

**Deleted in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/config/WebMvcConfig.kt`

**Modified in `kotlin-saas-template`:**
- `app/src/main/resources/application.yml` — add `saasstarter.tenant.*` and `saasstarter.rate-limit.*` blocks
- `gradle/libs.versions.toml` — bump starter to `0.6.0-SNAPSHOT` then `0.6.0`

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/WebMvcAutoConfigSmokeTest.kt`

---

### Task 1: Add `tenant` and `rate-limit` groups to `SaasStarterProperties`

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-starter`.

**Files:**
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`

- [ ] **Step 1: Add a failing test for both groups**

In `SaasStarterAutoConfigurationTest.kt`:

```kotlin
import strikt.assertions.containsExactly

    @Test
    fun `tenant and rate-limit path patterns bind from properties`() {
        contextRunner
            .withPropertyValues(
                "saasstarter.tenant.path-patterns=/app/**,/dashboard/**",
                "saasstarter.tenant.exclude-path-patterns=/webhooks/**,/",
                "saasstarter.rate-limit.path-patterns=/webhooks/**",
            )
            .run { context ->
                val props = context.getBean(SaasStarterProperties::class.java)
                expectThat(props.tenant.pathPatterns).containsExactly("/app/**", "/dashboard/**")
                expectThat(props.tenant.excludePathPatterns).containsExactly("/webhooks/**", "/")
                expectThat(props.rateLimit.pathPatterns).containsExactly("/webhooks/**")
            }
    }
```

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: tenant` and `Unresolved reference: rateLimit`.

- [ ] **Step 3: Add the `Tenant` and `RateLimit` data classes**

Update `SaasStarterProperties.kt`:

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "saasstarter")
data class SaasStarterProperties(
    val enabled: Boolean = true,
    val session: Session = Session(),
    val jobs: Jobs = Jobs(),
    val cache: Cache = Cache(),
    val tenant: Tenant = Tenant(),
    val rateLimit: RateLimit = RateLimit(),
) {
    data class Session(val enabled: Boolean = true)
    data class Jobs(val enabled: Boolean = true)

    data class Cache(
        val enabled: Boolean = true,
        val defaultTtl: Duration = Duration.ofMinutes(10),
        val configurations: Map<String, CacheEntry> = emptyMap(),
    ) {
        data class CacheEntry(val ttl: Duration? = null)
    }

    data class Tenant(
        val enabled: Boolean = true,
        /** Path patterns where TenantInterceptor runs (and resolves tenant from JWT). */
        val pathPatterns: List<String> = emptyList(),
        /** Path patterns to exclude even if matched by pathPatterns. */
        val excludePathPatterns: List<String> = emptyList(),
    )

    data class RateLimit(
        val enabled: Boolean = true,
        /** Path patterns where RateLimitInterceptor runs. */
        val pathPatterns: List<String> = emptyList(),
    )
}
```

The kebab-case `rate-limit` maps to `rateLimit` via Spring's relaxed binding.

- [ ] **Step 4: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: add tenant and rate-limit groups to SaasStarterProperties

Adds saasstarter.tenant.path-patterns / exclude-path-patterns and
saasstarter.rate-limit.path-patterns for the upcoming WebMvcAutoConfiguration.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Drop `@Component` / `@Service` from interceptor and rate-limiter classes

**Files:**
- Modify: `src/main/kotlin/org/granchi/saasstarter/tenant/TenantInterceptor.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/ratelimit/RateLimitInterceptor.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/ratelimit/RateLimiter.kt`

- [ ] **Step 1: Remove `@Component` from `TenantInterceptor.kt`**

Delete the import `import org.springframework.stereotype.Component` and the `@Component` annotation on the class.

- [ ] **Step 2: Remove `@Component` from `RateLimitInterceptor.kt`**

Same pattern.

- [ ] **Step 3: Remove `@Service` from `RateLimiter.kt`**

Delete `import org.springframework.stereotype.Service` and the `@Service` annotation on the class.

- [ ] **Step 4: Verify the project still compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. No tests yet exercise these classes — Task 4 introduces wiring tests that depend on them being registered as `@Bean`.

- [ ] **Step 5: Don't commit yet** — combined with Task 5.

---

### Task 3: Write the failing test for `WebMvcAutoConfiguration`

**Files:**
- Create: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/WebMvcAutoConfigurationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.granchi.saasstarter.ratelimit.RateLimitInterceptor
import org.granchi.saasstarter.ratelimit.RateLimiter
import org.granchi.saasstarter.tenant.TenantInterceptor
import org.granchi.saasstarter.tenant.TenantResolver
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import java.util.UUID

class WebMvcAutoConfigurationTest {

    @Suppress("UNCHECKED_CAST")
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration::class.java))
        .withBean(
            "redisTemplate",
            RedisTemplate::class.java as Class<RedisTemplate<String, Any>>,
            { org.mockito.kotlin.mock<RedisTemplate<String, Any>>() }
        )
        .withBean(TenantResolver::class.java, { TenantResolver { _ -> UUID.randomUUID() } })

    @Test
    fun `RateLimiter, TenantInterceptor and RateLimitInterceptor are registered`() {
        contextRunner.run { context ->
            expectThat(context.getBeansOfType(RateLimiter::class.java)).hasSize(1)
            expectThat(context.getBeansOfType(TenantInterceptor::class.java)).hasSize(1)
            expectThat(context.getBeansOfType(RateLimitInterceptor::class.java)).hasSize(1)
        }
    }

    @Test
    fun `WebMvcAutoConfiguration is itself a WebMvcConfigurer bean`() {
        contextRunner.run { context ->
            val configurers = context.getBeansOfType(WebMvcConfigurer::class.java)
            // The autoconfig class registers itself
            expectThat(configurers.keys).contains("webMvcAutoConfiguration")
        }
    }

    @Test
    fun `tenant interceptor is skipped when tenant enabled is false`() {
        contextRunner
            .withPropertyValues("saasstarter.tenant.enabled=false")
            .run { context ->
                expectThat(context.getBeansOfType(TenantInterceptor::class.java)).hasSize(0)
            }
    }

    @Test
    fun `rate limit interceptor is skipped when rate-limit enabled is false`() {
        contextRunner
            .withPropertyValues("saasstarter.rate-limit.enabled=false")
            .run { context ->
                expectThat(context.getBeansOfType(RateLimitInterceptor::class.java)).hasSize(0)
            }
    }
}
```

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.WebMvcAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: WebMvcAutoConfiguration`.

---

### Task 4: Implement `WebMvcAutoConfiguration`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/WebMvcAutoConfiguration.kt`

- [ ] **Step 1: Write the autoconfig**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.granchi.saasstarter.ratelimit.RateLimitInterceptor
import org.granchi.saasstarter.ratelimit.RateLimiter
import org.granchi.saasstarter.tenant.TenantInterceptor
import org.granchi.saasstarter.tenant.TenantResolver
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Wires the starter's web MVC interceptors and the supporting [RateLimiter].
 *
 * - [TenantInterceptor]: declared as a bean if `saasstarter.tenant.enabled=true`
 *   (default) and a [TenantResolver] bean is available. Registered on the path
 *   patterns from `saasstarter.tenant.path-patterns`, excluding
 *   `saasstarter.tenant.exclude-path-patterns`.
 * - [RateLimitInterceptor]: declared if `saasstarter.rate-limit.enabled=true`
 *   (default). Registered on `saasstarter.rate-limit.path-patterns`.
 * - [RateLimiter]: declared unconditionally when the autoconfig is active and
 *   a `RedisTemplate<String, Any>` bean is available.
 *
 * Implements [WebMvcConfigurer] itself to register the interceptors with the
 * configured path patterns.
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer::class)
@EnableConfigurationProperties(SaasStarterProperties::class)
class WebMvcAutoConfiguration(
    private val properties: SaasStarterProperties,
    private val tenantInterceptorProvider: org.springframework.beans.factory.ObjectProvider<TenantInterceptor>,
    private val rateLimitInterceptorProvider: org.springframework.beans.factory.ObjectProvider<RateLimitInterceptor>,
) : WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisTemplate::class)
    fun rateLimiter(
        @Suppress("UNCHECKED_CAST")
        redisTemplate: RedisTemplate<String, Any>,
    ): RateLimiter = RateLimiter(redisTemplate)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RateLimiter::class)
    @ConditionalOnProperty(
        prefix = "saasstarter.rate-limit",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun rateLimitInterceptor(rateLimiter: RateLimiter): RateLimitInterceptor =
        RateLimitInterceptor(rateLimiter)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TenantResolver::class)
    @ConditionalOnProperty(
        prefix = "saasstarter.tenant",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun tenantInterceptor(tenantResolver: TenantResolver): TenantInterceptor =
        TenantInterceptor(tenantResolver)

    override fun addInterceptors(registry: InterceptorRegistry) {
        rateLimitInterceptorProvider.ifAvailable { interceptor ->
            if (properties.rateLimit.pathPatterns.isNotEmpty()) {
                registry.addInterceptor(interceptor)
                    .addPathPatterns(*properties.rateLimit.pathPatterns.toTypedArray())
            }
        }
        tenantInterceptorProvider.ifAvailable { interceptor ->
            if (properties.tenant.pathPatterns.isNotEmpty()) {
                val registration = registry.addInterceptor(interceptor)
                    .addPathPatterns(*properties.tenant.pathPatterns.toTypedArray())
                if (properties.tenant.excludePathPatterns.isNotEmpty()) {
                    registration.excludePathPatterns(*properties.tenant.excludePathPatterns.toTypedArray())
                }
            }
        }
    }
}
```

`ObjectProvider` is the canonical Spring pattern for late-bound optional dependencies — it's lazy (the underlying beans aren't resolved at construction time), so injecting providers for beans defined by this same class doesn't create a cycle. `ifAvailable` runs the closure only if the bean is actually present (after the property-based `@ConditionalOnProperty` gating).

- [ ] **Step 2: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.WebMvcAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL.

If "WebMvcAutoConfiguration is itself a WebMvcConfigurer bean" fails because the registered bean name differs, query by type:

```kotlin
expectThat(configurers.values.any { it is WebMvcAutoConfiguration }).isTrue()
```

---

### Task 5: Register `WebMvcAutoConfiguration` in the imports file and commit

**Files:**
- Modify: `src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`

- [ ] **Step 1: Append the new FQCN**

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
org.granchi.saasstarter.autoconfigure.JobRunrAutoConfiguration
org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration
org.granchi.saasstarter.autoconfigure.WebMvcAutoConfiguration
```

- [ ] **Step 2: Add the import test**

```kotlin
    @Test
    fun `imports file lists WebMvcAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()
        expectThat(resource!!.readText())
            .contains("org.granchi.saasstarter.autoconfigure.WebMvcAutoConfiguration")
    }
```

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit Tasks 2-5 together**

```bash
git add src/main/kotlin/org/granchi/saasstarter/tenant/TenantInterceptor.kt \
        src/main/kotlin/org/granchi/saasstarter/ratelimit/RateLimitInterceptor.kt \
        src/main/kotlin/org/granchi/saasstarter/ratelimit/RateLimiter.kt \
        src/main/kotlin/org/granchi/saasstarter/autoconfigure/WebMvcAutoConfiguration.kt \
        src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/WebMvcAutoConfigurationTest.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt
git commit -m "$(cat <<'EOF'
feat: add WebMvcAutoConfiguration with property-driven interceptor paths

- Declares TenantInterceptor, RateLimitInterceptor, and RateLimiter as @Bean.
- Implements WebMvcConfigurer to register interceptors on
  saasstarter.tenant.path-patterns / exclude-path-patterns and
  saasstarter.rate-limit.path-patterns.
- Drops @Component / @Service from the moved classes (stereotypes were not
  honored by consumers' @ComponentScan).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Publish `0.6.0-SNAPSHOT` to mavenLocal

- [ ] **Step 1: Publish**

```bash
./gradlew publishToMavenLocal -Pversion=0.6.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL.

---

### Task 7: (Reserved) — no further starter work in this plan.

---

### Task 8: Bump starter version and add tenant + rate-limit properties to template

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-template`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Bump starter version**

```toml
kotlin-saas-starter = "0.6.0-SNAPSHOT"
```

- [ ] **Step 2: Add path patterns to `application.yml`**

Append to the existing `saasstarter:` block (added in Plan 4):

```yaml
saasstarter:
  cache:
    # ... existing cache config from Plan 4 ...
  tenant:
    path-patterns:
      - /app/**
      - /dashboard/**
      - /billing/**
      - /organization/**
    exclude-path-patterns:
      - /webhooks/**
      - /
      - /pricing
      - /docs/**
  rate-limit:
    path-patterns:
      - /webhooks/**
```

These match the previous hardcoded patterns in `WebMvcConfig.kt`.

- [ ] **Step 3: Refresh dependencies**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: `org.granchi:kotlin-saas-starter:0.6.0-SNAPSHOT`.

- [ ] **Step 4: Don't commit yet** — Tasks 9-10 finalize.

---

### Task 9: Delete `WebMvcConfig.kt` from template

**Files:**
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/config/WebMvcConfig.kt`

- [ ] **Step 1: Remove the file**

```bash
git rm app/src/main/kotlin/org/granchi/saastemplate/config/WebMvcConfig.kt
```

- [ ] **Step 2: Verify the project still compiles**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 10: Add a smoke test confirming interceptor wiring

**Files:**
- Create: `app/src/test/kotlin/org/granchi/saastemplate/integration/WebMvcAutoConfigSmokeTest.kt`

- [ ] **Step 1: Write the smoke test**

```kotlin
package org.granchi.saastemplate.integration

import org.granchi.saasstarter.autoconfigure.WebMvcAutoConfiguration
import org.granchi.saasstarter.ratelimit.RateLimitInterceptor
import org.granchi.saasstarter.ratelimit.RateLimiter
import org.granchi.saasstarter.tenant.TenantInterceptor
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize

@Tag("integration")
@SpringBootTest
class WebMvcAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `WebMvcAutoConfiguration is discovered`() {
        expectThat(context.getBeansOfType(WebMvcAutoConfiguration::class.java)).hasSize(1)
    }

    @Test
    fun `TenantInterceptor, RateLimitInterceptor, and RateLimiter are wired`() {
        expectThat(context.getBeansOfType(TenantInterceptor::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(RateLimitInterceptor::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(RateLimiter::class.java)).hasSize(1)
    }

    @Test
    fun `tenant path patterns bind from application yml`() {
        val props = context.getBean(
            org.granchi.saasstarter.autoconfigure.SaasStarterProperties::class.java
        )
        expectThat(props.tenant.pathPatterns).contains(
            "/app/**", "/dashboard/**", "/billing/**", "/organization/**"
        )
        expectThat(props.tenant.excludePathPatterns).contains(
            "/webhooks/**", "/", "/pricing", "/docs/**"
        )
        expectThat(props.rateLimit.pathPatterns).contains("/webhooks/**")
    }
}
```

- [ ] **Step 2: Run the smoke test**

```bash
./gradlew :app:integrationTest --tests "org.granchi.saastemplate.integration.WebMvcAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL. All three tests pass.

- [ ] **Step 3: Run full test suite**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL. `TenantIsolationTest` continues to pass — `TenantInterceptor` and `MemberTenantResolver` collaboration is unchanged.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/resources/application.yml \
        app/src/main/kotlin/org/granchi/saastemplate/config/WebMvcConfig.kt \
        app/src/test/kotlin/org/granchi/saastemplate/integration/WebMvcAutoConfigSmokeTest.kt
git commit -m "$(cat <<'EOF'
refactor: delegate WebMVC interceptor wiring to kotlin-saas-starter 0.6.0

WebMvcConfig.kt is removed. WebMvcAutoConfiguration in the starter declares
TenantInterceptor, RateLimitInterceptor, and RateLimiter as beans and
registers them on path patterns from saasstarter.tenant.* and
saasstarter.rate-limit.* (configured in application.yml).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Open release PRs and finalize

- [ ] **Step 1: Push starter branch and open PR**

In `/var/home/serandel/Projects/kotlin-saas-starter`:

```bash
git push -u origin <branch-name>
gh pr create --title "feat: add WebMvcAutoConfiguration" \
             --body "$(cat <<'EOF'
## Summary

- Adds WebMvcAutoConfiguration declaring TenantInterceptor, RateLimitInterceptor,
  and RateLimiter as @Bean with registration driven by saasstarter.tenant.* and
  saasstarter.rate-limit.* properties.
- Drops @Component / @Service from the moved classes.

Implements Plan 5 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Test plan

- [x] WebMvcAutoConfigurationTest: bean wiring + kill switches
- [x] AutoConfigurationImportsTest covers the new entry
- [x] Template WebMvcAutoConfigSmokeTest verifies path patterns bind from application.yml

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI, merge PRs**

After release-please's PR merges, `0.6.0` is published.

- [ ] **Step 3: Bump template to released `0.6.0`**

```bash
sed -i 's/kotlin-saas-starter = "0.6.0-SNAPSHOT"/kotlin-saas-starter = "0.6.0"/' \
       gradle/libs.versions.toml
```

- [ ] **Step 4: Re-run tests**

```bash
./gradlew :app:test :app:integrationTest --refresh-dependencies
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit and push**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
build: bump kotlin-saas-starter to 0.6.0

Replaces 0.6.0-SNAPSHOT with the released version (Plan 5: WebMvcAutoConfiguration).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

---

## Done When

- `kotlin-saas-starter` 0.6.0 is published with `WebMvcAutoConfiguration`.
- Template depends on `0.6.0`, has no `WebMvcConfig.kt`, and `WebMvcAutoConfigSmokeTest` passes.
- Tenant resolution and rate limiting still apply to the same path patterns as before.
- `./gradlew :app:test :app:integrationTest` is green in the template.
- Template's `config/` package now contains 2 files: `MemberTenantResolver`, `SecurityConfig`.
- Both repos have their commits pushed.
