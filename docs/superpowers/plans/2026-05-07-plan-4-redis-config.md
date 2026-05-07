# Plan 4 — Move `RedisConfig` to Starter (Split) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the generic Redis wiring (`RedisTemplate<String, Any>` with JSON serialization, `RedisCacheManager` with `@EnableCaching`) to the starter as `RedisAutoConfiguration`. Cache names and TTLs migrate from hardcoded Kotlin to a `Map<String, CacheEntry>` property under `saasstarter.cache.configurations`, configured per-app in `application.yml`.

**Architecture:** The starter declares `RedisTemplate<String, Any>` and `RedisCacheManager` as `@Bean`s gated by `@ConditionalOnClass(RedisConnectionFactory::class)`, `@ConditionalOnBean(RedisConnectionFactory::class)`, and `@ConditionalOnMissingBean`. Cache configurations come from `SaasStarterProperties.Cache.configurations` (a map of cache name → `CacheEntry(ttl)`); the autoconfig walks the map and builds per-cache `RedisCacheConfiguration` instances. The template's `application.yml` populates the map with the same three caches it had hardcoded (`tenant-by-user`, `organization`, `subscription`).

**Tech Stack:** Spring Data Redis, Jackson JSON, Spring Boot autoconfigure.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 4.

**Prerequisites:** Plans 0-3 complete. The template depends on `kotlin-saas-starter:0.4.0`.

**Cross-repo:** Tasks 1-5 in `kotlin-saas-starter`. Tasks 6-9 in `kotlin-saas-template`. Task 10 finalizes as `0.5.0`.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfiguration.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfigurationTest.kt`

**Modified in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt` — add `Cache` group with `defaultTtl` and `configurations` map
- `src/main/resources/META-INF/spring/...AutoConfiguration.imports` — add the new FQCN
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt` — assert cache property binding

**Deleted in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/config/RedisConfig.kt`

**Modified in `kotlin-saas-template`:**
- `app/src/main/resources/application.yml` — add `saasstarter.cache.*` block
- `gradle/libs.versions.toml` — bump starter to `0.5.0-SNAPSHOT` then `0.5.0`

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/RedisAutoConfigSmokeTest.kt`

---

### Task 1: Add `Cache` group to `SaasStarterProperties` (failing test first)

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-starter`.

**Files:**
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterAutoConfigurationTest.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/SaasStarterProperties.kt`

- [ ] **Step 1: Add a failing test for the cache binding**

In `SaasStarterAutoConfigurationTest.kt`, add:

```kotlin
import java.time.Duration
import strikt.assertions.containsKey
import strikt.assertions.isEqualTo

    @Test
    fun `cache configurations bind via saasstarter cache configurations`() {
        contextRunner
            .withPropertyValues(
                "saasstarter.cache.default-ttl=10m",
                "saasstarter.cache.configurations.tenant-by-user.ttl=5m",
                "saasstarter.cache.configurations.organization.ttl=30m",
            )
            .run { context ->
                val props = context.getBean(SaasStarterProperties::class.java)
                expectThat(props.cache.defaultTtl).isEqualTo(Duration.ofMinutes(10))
                expectThat(props.cache.configurations).containsKey("tenant-by-user")
                expectThat(props.cache.configurations["tenant-by-user"]!!.ttl)
                    .isEqualTo(Duration.ofMinutes(5))
                expectThat(props.cache.configurations["organization"]!!.ttl)
                    .isEqualTo(Duration.ofMinutes(30))
            }
    }
```

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: cache`.

- [ ] **Step 3: Add the `Cache` data class**

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
) {
    data class Session(
        val enabled: Boolean = true,
    )

    data class Jobs(
        val enabled: Boolean = true,
    )

    data class Cache(
        val enabled: Boolean = true,
        /** Fallback TTL applied to caches that don't declare their own. */
        val defaultTtl: Duration = Duration.ofMinutes(10),
        /**
         * Per-cache configuration. Map key is the cache name (matching
         * `@Cacheable("name")` references); value declares its TTL.
         */
        val configurations: Map<String, CacheEntry> = emptyMap(),
    ) {
        data class CacheEntry(
            val ttl: Duration? = null,
        )
    }
}
```

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
feat: add cache group to SaasStarterProperties

saasstarter.cache exposes default-ttl plus a per-cache configurations map
for the upcoming RedisAutoConfiguration.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Write the failing test for `RedisAutoConfiguration`

**Files:**
- Create: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfigurationTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isA

class RedisAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration::class.java))
        // Provide a stub connection factory so beans that depend on it can wire.
        .withBean(RedisConnectionFactory::class.java, { org.mockito.kotlin.mock() })

    @Test
    fun `RedisTemplate is registered with String key and JSON value serializers`() {
        contextRunner.run { context ->
            @Suppress("UNCHECKED_CAST")
            val template = context.getBean(RedisTemplate::class.java) as RedisTemplate<String, Any>
            expectThat(template.keySerializer).isA<StringRedisSerializer>()
            expectThat(template.valueSerializer).isA<GenericJackson2JsonRedisSerializer>()
        }
    }

    @Test
    fun `RedisCacheManager is registered`() {
        contextRunner.run { context ->
            val managers = context.getBeansOfType(RedisCacheManager::class.java)
            expectThat(managers).hasSize(1)
        }
    }

    @Test
    fun `cache manager honours per-cache TTLs from configurations property`() {
        contextRunner
            .withPropertyValues(
                "saasstarter.cache.default-ttl=10m",
                "saasstarter.cache.configurations.tenant-by-user.ttl=5m",
            )
            .run { context ->
                val manager = context.getBean(RedisCacheManager::class.java)
                expectThat(manager.cacheNames).contains("tenant-by-user")
            }
    }

    @Test
    fun `autoconfig is skipped when cache enabled is false`() {
        contextRunner
            .withPropertyValues("saasstarter.cache.enabled=false")
            .run { context ->
                expectThat(context.getBeansOfType(RedisCacheManager::class.java)).hasSize(0)
            }
    }
}
```

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.RedisAutoConfigurationTest"
```

Expected: BUILD FAILED. `Unresolved reference: RedisAutoConfiguration`.

---

### Task 3: Implement `RedisAutoConfiguration`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfiguration.kt`

- [ ] **Step 1: Write the autoconfig**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis-backed caching with String keys and JSON values.
 *
 * Provides:
 * - [RedisTemplate]<String, Any> with String key + JSON value serialization
 * - [RedisCacheManager] populated from [SaasStarterProperties.Cache.configurations]
 *
 * Disabled if `saasstarter.cache.enabled=false`. Backs off if the consumer
 * defines its own `RedisTemplate` or `RedisCacheManager`.
 */
@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory::class)
@ConditionalOnBean(RedisConnectionFactory::class)
@ConditionalOnProperty(
    prefix = "saasstarter.cache",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(SaasStarterProperties::class)
@EnableCaching
class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun redisTemplate(factory: RedisConnectionFactory): RedisTemplate<String, Any> =
        RedisTemplate<String, Any>().apply {
            connectionFactory = factory
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
        }

    @Bean
    @ConditionalOnMissingBean
    fun cacheManager(
        factory: RedisConnectionFactory,
        properties: SaasStarterProperties,
    ): RedisCacheManager {
        val defaults = baseConfig().entryTtl(properties.cache.defaultTtl)
        val perCache = properties.cache.configurations.mapValues { (_, entry) ->
            entry.ttl?.let { defaults.entryTtl(it) } ?: defaults
        }
        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(perCache)
            .build()
    }

    private fun baseConfig(): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer())
            )
}
```

- [ ] **Step 2: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.RedisAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL. All four tests pass.

If "RedisCacheManager is registered" fails because the mocked `RedisConnectionFactory` returns nulls during `RedisCacheManager.builder()`, replace the `mock()` with a `LettuceConnectionFactory()` instance (no `afterPropertiesSet()` needed for the build call — it's lazy):

```kotlin
.withBean(
    RedisConnectionFactory::class.java,
    { org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory() }
)
```

---

### Task 4: Register `RedisAutoConfiguration` in the imports file

**Files:**
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`

- [ ] **Step 1: Append the new FQCN**

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
org.granchi.saasstarter.autoconfigure.JobRunrAutoConfiguration
org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration
```

- [ ] **Step 2: Add the corresponding test**

```kotlin
    @Test
    fun `imports file lists RedisAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()
        expectThat(resource!!.readText())
            .contains("org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration")
    }
```

- [ ] **Step 3: Run all tests**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfiguration.kt \
        src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfigurationTest.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt
git commit -m "$(cat <<'EOF'
feat: add RedisAutoConfiguration with property-driven cache layout

Provides RedisTemplate<String, Any> with JSON serialization and a
RedisCacheManager built from saasstarter.cache.configurations.
Per-cache TTLs are configured by the consuming app via properties.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Publish `0.5.0-SNAPSHOT` to mavenLocal

- [ ] **Step 1: Publish**

```bash
./gradlew publishToMavenLocal -Pversion=0.5.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL.

---

### Task 6: Bump starter version and add cache properties to template

All work in this task happens inside `/var/home/serandel/Projects/kotlin-saas-template`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Bump starter version**

```toml
kotlin-saas-starter = "0.5.0-SNAPSHOT"
```

- [ ] **Step 2: Add `saasstarter.cache` block to `application.yml`**

Insert after the existing `spring:` block (around line 14):

```yaml
saasstarter:
  cache:
    default-ttl: 10m
    configurations:
      # Tenant lookup — hit on every request, short TTL to pick up membership changes fast
      tenant-by-user:
        ttl: 5m
      # Organization data — changes rarely
      organization:
        ttl: 30m
      # Subscription status — needs to be fresh for billing checks
      subscription:
        ttl: 5m
```

These values match the previous hardcoded TTLs in `RedisConfig.kt`.

- [ ] **Step 3: Refresh dependencies**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: `org.granchi:kotlin-saas-starter:0.5.0-SNAPSHOT`.

- [ ] **Step 4: Don't commit yet** — Tasks 7-8 finish the migration.

---

### Task 7: Delete `RedisConfig.kt` from the template

**Files:**
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/config/RedisConfig.kt`

- [ ] **Step 1: Remove the file**

```bash
git rm app/src/main/kotlin/org/granchi/saastemplate/config/RedisConfig.kt
```

- [ ] **Step 2: Verify the project still compiles**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL. The starter's `RedisAutoConfiguration` provides equivalent beans.

---

### Task 8: Add a smoke test confirming cache wiring

**Files:**
- Create: `app/src/test/kotlin/org/granchi/saastemplate/integration/RedisAutoConfigSmokeTest.kt`

- [ ] **Step 1: Write the smoke test**

```kotlin
package org.granchi.saastemplate.integration

import org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration
import org.granchi.saasstarter.autoconfigure.SaasStarterProperties
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.core.RedisTemplate
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsKey
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Duration

@Tag("integration")
@SpringBootTest
class RedisAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var properties: SaasStarterProperties

    @Test
    fun `RedisAutoConfiguration is discovered and active`() {
        expectThat(context.getBeansOfType(RedisAutoConfiguration::class.java)).hasSize(1)
    }

    @Test
    fun `RedisTemplate and RedisCacheManager beans are wired`() {
        expectThat(context.getBeansOfType(RedisTemplate::class.java).size).isEqualTo(1)
        expectThat(context.getBeansOfType(RedisCacheManager::class.java).size).isEqualTo(1)
    }

    @Test
    fun `cache properties bind from application yml with the expected TTLs`() {
        val configurations = properties.cache.configurations
        expectThat(configurations).containsKey("tenant-by-user")
        expectThat(configurations["tenant-by-user"]!!.ttl).isEqualTo(Duration.ofMinutes(5))
        expectThat(configurations["organization"]!!.ttl).isEqualTo(Duration.ofMinutes(30))
        expectThat(configurations["subscription"]!!.ttl).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun `cache manager exposes the named caches`() {
        val cacheManager = context.getBean(RedisCacheManager::class.java)
        expectThat(cacheManager.cacheNames).contains("tenant-by-user", "organization", "subscription")
    }
}
```

- [ ] **Step 2: Run the smoke test**

```bash
./gradlew :app:integrationTest --tests "org.granchi.saastemplate.integration.RedisAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL. All four tests pass.

- [ ] **Step 3: Run the full test suite**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL. The existing `OrganizationService` `@CacheEvict("tenant-by-user", ...)` and `OrganizationRepository` `@Cacheable("tenant-by-user", ...)` continue to function — they reference cache names that the new `RedisCacheManager` exposes.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/resources/application.yml \
        app/src/main/kotlin/org/granchi/saastemplate/config/RedisConfig.kt \
        app/src/test/kotlin/org/granchi/saastemplate/integration/RedisAutoConfigSmokeTest.kt
git commit -m "$(cat <<'EOF'
refactor: delegate Redis caching to kotlin-saas-starter 0.5.0

RedisConfig.kt is removed. RedisAutoConfiguration in the starter provides
RedisTemplate<String, Any> and RedisCacheManager built from
saasstarter.cache.configurations (configured in application.yml).
Smoke test verifies cache TTLs match prior hardcoded values.

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
gh pr create --title "feat: add RedisAutoConfiguration with property-driven cache layout" \
             --body "$(cat <<'EOF'
## Summary

- Adds RedisAutoConfiguration providing RedisTemplate<String, Any> with JSON serialization and RedisCacheManager.
- Cache names + TTLs configured via saasstarter.cache.configurations map (per-cache).
- @ConditionalOnMissingBean lets consumers override either bean.

Implements Plan 4 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Test plan

- [x] RedisAutoConfigurationTest: bean wiring, kill switch, per-cache TTL plumbing
- [x] AutoConfigurationImportsTest covers the new entry
- [x] Template RedisAutoConfigSmokeTest verifies properties bind from application.yml and cache names match

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI, merge PRs**

After the release-please PR merges, `0.5.0` is published.

- [ ] **Step 3: Bump template to released `0.5.0`**

```bash
sed -i 's/kotlin-saas-starter = "0.5.0-SNAPSHOT"/kotlin-saas-starter = "0.5.0"/' \
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
build: bump kotlin-saas-starter to 0.5.0

Replaces 0.5.0-SNAPSHOT with the released version (Plan 4: RedisAutoConfiguration).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

---

## Done When

- `kotlin-saas-starter` 0.5.0 is published with `RedisAutoConfiguration`.
- Template depends on `0.5.0`, has no `RedisConfig.kt`, and `RedisAutoConfigSmokeTest` passes.
- The three named caches (`tenant-by-user`, `organization`, `subscription`) are still functional — `@Cacheable` and `@CacheEvict` continue to work in `OrganizationRepository` and `OrganizationService`.
- `./gradlew :app:test :app:integrationTest` is green in the template.
- Template's `config/` package now contains 3 files: `MemberTenantResolver`, `SecurityConfig`, `WebMvcConfig`.
- Both repos have their commits pushed.
