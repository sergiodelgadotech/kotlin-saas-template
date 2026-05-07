# Plan 6 — Move Multi-Tenant Model to Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the `Organization` / `Member` entities, their repositories, validations, and `OrganizationService` from the template to the starter. Introduce `MemberRole` as an interface with a `DefaultMemberRole` enum implementation. Rename `Member.zitadelUserId` to `externalUserId` (provider-agnostic). Ship the corresponding Flyway migration in the starter under `db/migration/saasstarter/`. Template keeps only `OrganizationController` and `MemberTenantResolver` for org-related code.

**Architecture:** The starter gains a new `organization` package containing entities, repositories, validations, and the service — wired via a new `OrganizationAutoConfiguration` that uses `@EnableJdbcRepositories` to make Spring Data JDBC discover the repositories in the starter's package. `RedisLockService` (used by the org service) drops its `@Service` annotation and is now declared by `RedisAutoConfiguration` (modifies Plan 4's autoconfig). The template's `V1__init.sql` is renumbered to `V200__app_init.sql` and stripped of `organizations`/`members` `CREATE TABLE`s; `V2__jobs.sql` and `V3__analysis.sql` follow as `V201`/`V202`. Starter's `V100__starter_baseline.sql` runs first, creating the multi-tenant tables before the template's app-specific tables (which still reference `organizations` via FK from `subscriptions`).

**Tech Stack:** Spring Data JDBC, Konform validation, Flyway, PostgreSQL.

**Spec reference:** `docs/superpowers/specs/2026-05-07-starter-template-split-design.md` § Plan 6 + § Architectural decision 5 (Flyway baseline handling).

**Prerequisites:** Plans 0-5 complete. Template depends on `kotlin-saas-starter:0.6.0`. No production deployments — local dev databases will be reset.

**Cross-repo:** Tasks 1-12 in `kotlin-saas-starter`. Tasks 13-19 in `kotlin-saas-template`. Task 20 finalizes as `0.7.0`.

**Bug fix (incidental):** `OrganizationService.inviteMember` references `existsByOrganizationIdAndClerkUserId` but the repository defines `existsByOrganizationIdAndZitadelUserId`. The current code does not compile cleanly and is fixed during the migration as `existsByOrganizationIdAndExternalUserId`.

---

## File Structure

**Created in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/organization/MemberRole.kt` — interface + `DefaultMemberRole` enum
- `src/main/kotlin/org/granchi/saasstarter/organization/Organization.kt` — entity + `Member` entity (renamed `externalUserId`)
- `src/main/kotlin/org/granchi/saasstarter/organization/OrganizationRepository.kt` — `OrganizationRepository`, `MemberRepository`
- `src/main/kotlin/org/granchi/saasstarter/organization/OrganizationValidations.kt` — commands + Konform validators
- `src/main/kotlin/org/granchi/saasstarter/organization/OrganizationService.kt` — service (no `@Service`)
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt`
- `src/main/resources/db/migration/saasstarter/V100__starter_baseline.sql`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/OrganizationAutoConfigurationTest.kt`
- `src/test/kotlin/org/granchi/saasstarter/organization/OrganizationServiceIntegrationTest.kt` — Testcontainers + real DB

**Modified in `kotlin-saas-starter`:**
- `src/main/kotlin/org/granchi/saasstarter/lock/RedisLockService.kt` — drop `@Service`
- `src/main/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfiguration.kt` — add `@Bean` for `RedisLockService`
- `src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`
- `src/test/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfigurationTest.kt` — assert `RedisLockService` bean
- `build.gradle.kts` — add `flyway-core` (test only) and `postgresql` test dependencies if missing

**Renamed in `kotlin-saas-template`:**
- `app/src/main/resources/db/migration/V1__init.sql` → `V200__app_init.sql` (and edited)
- `app/src/main/resources/db/migration/V2__jobs.sql` → `V201__jobs.sql`
- `app/src/main/resources/db/migration/V3__analysis.sql` → `V202__analysis.sql`

**Modified in `kotlin-saas-template`:**
- `app/src/main/resources/db/migration/V200__app_init.sql` — drop `organizations` and `members` table definitions; keep `subscriptions`
- `app/src/main/resources/application.yml` — set Flyway `locations` to scan both classpaths
- `app/src/main/kotlin/org/granchi/saastemplate/config/MemberTenantResolver.kt` — import `MemberRepository` from starter
- `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationController.kt` — import `Organization`, `OrganizationService` from starter
- `app/src/main/kotlin/org/granchi/saastemplate/billing/BillingService.kt` — adjust `Subscription` reference if it imports `Organization` directly (verify during execution)
- `gradle/libs.versions.toml` — bump to `0.7.0-SNAPSHOT` then `0.7.0`

**Deleted in `kotlin-saas-template`:**
- `app/src/main/kotlin/org/granchi/saastemplate/organization/Organization.kt`
- `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationRepository.kt`
- `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationService.kt`
- `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationValidations.kt`

**Created in `kotlin-saas-template`:**
- `app/src/test/kotlin/org/granchi/saastemplate/integration/OrganizationAutoConfigSmokeTest.kt`

---

### Task 1: Create `MemberRole` interface and `DefaultMemberRole` enum in starter (TDD)

All starter tasks happen inside `/var/home/serandel/Projects/kotlin-saas-starter`.

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/organization/MemberRole.kt`
- Create: `src/test/kotlin/org/granchi/saasstarter/organization/MemberRoleTest.kt`

- [ ] **Step 1: Create the test directory and write the failing test**

```bash
mkdir -p src/test/kotlin/org/granchi/saasstarter/organization
mkdir -p src/main/kotlin/org/granchi/saasstarter/organization
```

Create `src/test/kotlin/org/granchi/saasstarter/organization/MemberRoleTest.kt`:

```kotlin
package org.granchi.saasstarter.organization

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class MemberRoleTest {

    @Test
    fun `default member role enum has the three canonical values`() {
        expectThat(DefaultMemberRole.values().toList())
            .containsExactly(DefaultMemberRole.OWNER, DefaultMemberRole.ADMIN, DefaultMemberRole.MEMBER)
    }

    @Test
    fun `default member role implements MemberRole`() {
        expectThat(DefaultMemberRole.OWNER as Any).isA<MemberRole>()
    }

    @Test
    fun `name property returns the enum name for serialization`() {
        expectThat(DefaultMemberRole.OWNER.name).isEqualTo("OWNER")
    }
}
```

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.organization.MemberRoleTest"
```

Expected: `Unresolved reference: MemberRole` and `Unresolved reference: DefaultMemberRole`.

- [ ] **Step 3: Implement `MemberRole.kt`**

```kotlin
package org.granchi.saasstarter.organization

/**
 * Marker interface for member roles. Apps replace [DefaultMemberRole] with their
 * own enum implementing this interface when the default OWNER/ADMIN/MEMBER set
 * isn't sufficient.
 */
interface MemberRole {
    /** Stable identifier used for persistence and serialization. */
    val name: String
}

enum class DefaultMemberRole : MemberRole {
    OWNER,
    ADMIN,
    MEMBER;
}
```

(Note: enum classes already have a `name` property, so the override happens automatically by name compatibility.)

- [ ] **Step 4: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.organization.MemberRoleTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/organization/MemberRole.kt \
        src/test/kotlin/org/granchi/saasstarter/organization/MemberRoleTest.kt
git commit -m "$(cat <<'EOF'
feat: add MemberRole interface with DefaultMemberRole enum

Introduces an extensibility seam for apps that need custom roles beyond
OWNER/ADMIN/MEMBER. The default enum will be used by Member entity in
follow-up commits.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Create `Organization` and `Member` entities in starter

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/organization/Organization.kt`

- [ ] **Step 1: Write the entities**

```kotlin
package org.granchi.saasstarter.organization

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("organizations")
data class Organization(
    @Id val id: UUID = UUID.randomUUID(),
    val name: String,
    val slug: String,
    val plan: String = "starter",
    val createdAt: Instant = Instant.now(),
)

@Table("members")
data class Member(
    @Id val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    /** Identity provider's user ID. App's [TenantResolver] maps it to the org. */
    val externalUserId: String,
    val role: String = DefaultMemberRole.MEMBER.name,
    val createdAt: Instant = Instant.now(),
)
```

`role` is stored as `String` so apps using a custom `MemberRole` enum aren't constrained by the starter's type. The default value is `DefaultMemberRole.MEMBER.name` (= `"MEMBER"`), matching the prior database default.

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Don't commit yet** — combined with Task 3.

---

### Task 3: Create repositories with cache annotation

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/organization/OrganizationRepository.kt`

- [ ] **Step 1: Write the repositories**

```kotlin
package org.granchi.saasstarter.organization

import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface OrganizationRepository : CrudRepository<Organization, UUID> {
    fun findBySlug(slug: String): Organization?
}

interface MemberRepository : CrudRepository<Member, UUID> {
    fun findByOrganizationId(organizationId: UUID): List<Member>
    fun findByExternalUserId(externalUserId: String): Member?

    /**
     * Cached — runs on every authenticated request.
     * Eviction is handled by [OrganizationService.removeMember].
     */
    @Cacheable("tenant-by-user", key = "#userId")
    @Query("SELECT organization_id FROM members WHERE external_user_id = :userId")
    fun findOrganizationIdByUserId(userId: String): UUID?

    fun existsByOrganizationIdAndExternalUserId(organizationId: UUID, externalUserId: String): Boolean
}
```

- [ ] **Step 2: Commit Tasks 2 + 3**

```bash
git add src/main/kotlin/org/granchi/saasstarter/organization/Organization.kt \
        src/main/kotlin/org/granchi/saasstarter/organization/OrganizationRepository.kt
git commit -m "$(cat <<'EOF'
feat: add Organization and Member entities with repositories

Member.externalUserId replaces zitadelUserId for provider-agnostic identity.
Repositories include the @Cacheable("tenant-by-user") query that runs on
every authenticated request.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Create `OrganizationValidations` in starter

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/organization/OrganizationValidations.kt`

- [ ] **Step 1: Add Konform dependency to starter**

Check `gradle/libs.versions.toml`:

```bash
grep -i "konform" gradle/libs.versions.toml
```

If absent, add:

```toml
[versions]
konform           = "0.10.0"

[libraries]
konform                       = { module = "io.konform:konform-jvm", version.ref = "konform" }
```

Add to `build.gradle.kts`:

```kotlin
api(libs.konform)
```

(`api`, not `compileOnly`, since `OrganizationValidations` exposes Konform's `Validation` type publicly.)

- [ ] **Step 2: Write the file**

```kotlin
package org.granchi.saasstarter.organization

import io.konform.validation.Validation
import io.konform.validation.jsonschema.maxLength
import io.konform.validation.jsonschema.minLength
import io.konform.validation.jsonschema.pattern

data class CreateOrganizationCommand(
    val name: String,
    val slug: String,
    val ownerExternalUserId: String,
)

data class InviteMemberCommand(
    val externalUserId: String,
    val role: String,
)

data class UpdateOrganizationCommand(
    val name: String,
)

object OrganizationValidations {

    val createOrganization = Validation<CreateOrganizationCommand> {
        CreateOrganizationCommand::name {
            minLength(2) hint "Name must be at least 2 characters"
            maxLength(255) hint "Name must be at most 255 characters"
        }
        CreateOrganizationCommand::slug {
            minLength(2) hint "Slug must be at least 2 characters"
            maxLength(100) hint "Slug must be at most 100 characters"
            pattern(Regex("^[a-z0-9-]+\$")) hint
                "Slug can only contain lowercase letters, numbers and hyphens"
        }
        CreateOrganizationCommand::ownerExternalUserId {
            minLength(1) hint "Owner is required"
        }
    }

    val updateOrganization = Validation<UpdateOrganizationCommand> {
        UpdateOrganizationCommand::name {
            minLength(2) hint "Name must be at least 2 characters"
            maxLength(255) hint "Name must be at most 255 characters"
        }
    }

    val inviteMember = Validation<InviteMemberCommand> {
        InviteMemberCommand::externalUserId {
            minLength(1) hint "User ID is required"
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/organization/OrganizationValidations.kt \
        gradle/libs.versions.toml \
        build.gradle.kts
git commit -m "$(cat <<'EOF'
feat: add OrganizationValidations with Konform commands

Renames CreateOrganizationCommand.ownerClerkUserId to ownerExternalUserId
and InviteMemberCommand.zitadelUserId to externalUserId for consistency
with the Member entity. Konform is exposed as `api` since validators
appear in public function signatures.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Drop `@Service` from `RedisLockService`, add bean to `RedisAutoConfiguration`

**Files:**
- Modify: `src/main/kotlin/org/granchi/saasstarter/lock/RedisLockService.kt`
- Modify: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfiguration.kt`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfigurationTest.kt`

- [ ] **Step 1: Drop `@Service` from `RedisLockService.kt`**

Delete the `import org.springframework.stereotype.Service` line and the `@Service` annotation on the class.

- [ ] **Step 2: Add a failing test for the new bean**

In `RedisAutoConfigurationTest.kt`, add:

```kotlin
import org.granchi.saasstarter.lock.RedisLockService

    @Test
    fun `RedisLockService is registered as a bean`() {
        contextRunner.run { context ->
            expectThat(context.getBeansOfType(RedisLockService::class.java)).hasSize(1)
        }
    }
```

- [ ] **Step 3: Run the test — fails**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.RedisAutoConfigurationTest"
```

Expected: BUILD FAILED. The bean isn't registered yet.

- [ ] **Step 4: Add the `@Bean` declaration**

In `RedisAutoConfiguration.kt`, add inside the class:

```kotlin
import org.granchi.saasstarter.lock.RedisLockService

    @Bean
    @ConditionalOnMissingBean
    fun redisLockService(
        @Suppress("UNCHECKED_CAST")
        redisTemplate: RedisTemplate<String, Any>,
    ): RedisLockService = RedisLockService(redisTemplate)
```

- [ ] **Step 5: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.RedisAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/granchi/saasstarter/lock/RedisLockService.kt \
        src/main/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfiguration.kt \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/RedisAutoConfigurationTest.kt
git commit -m "$(cat <<'EOF'
feat: declare RedisLockService bean in RedisAutoConfiguration

Drops @Service from RedisLockService (stereotype was not honored by
consumers' @ComponentScan). The autoconfig now provides the bean.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Create `OrganizationService` (no `@Service`)

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/organization/OrganizationService.kt`

- [ ] **Step 1: Write the service**

```kotlin
package org.granchi.saasstarter.organization

import org.granchi.saasstarter.lock.RedisLockService
import org.granchi.saasstarter.tenant.TenantContext
import org.granchi.saasstarter.web.NotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
open class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val memberRepository: MemberRepository,
    private val lockService: RedisLockService,
) {

    fun current(): Organization =
        organizationRepository.findById(TenantContext.get()).orElseThrow {
            NotFoundException("Organization not found")
        }

    fun members(): List<Member> =
        memberRepository.findByOrganizationId(TenantContext.get())

    fun inviteMember(externalUserId: String, role: String = DefaultMemberRole.MEMBER.name): Member {
        OrganizationValidations.inviteMember.validate(InviteMemberCommand(externalUserId, role))
        val orgId = TenantContext.get()
        return lockService.withLock("invite:$orgId:$externalUserId") {
            check(!memberRepository.existsByOrganizationIdAndExternalUserId(orgId, externalUserId)) {
                "User is already a member of this organization"
            }
            memberRepository.save(
                Member(organizationId = orgId, externalUserId = externalUserId, role = role)
            )
        }
    }

    @CacheEvict("tenant-by-user", key = "#result.externalUserId", condition = "#result != null")
    open fun removeMember(memberId: UUID): Member {
        val member = memberRepository.findById(memberId).orElseThrow {
            NotFoundException("Member not found")
        }
        check(member.organizationId == TenantContext.get()) {
            "Member does not belong to current organization"
        }
        memberRepository.delete(member)
        return member
    }

    fun updateName(name: String): Organization {
        OrganizationValidations.updateOrganization.validate(UpdateOrganizationCommand(name))
        val org = current()
        return organizationRepository.save(org.copy(name = name))
    }
}
```

Notes:
- The class is `open` (and `removeMember` is `open`) so Spring's CGLIB proxy for `@Transactional` and `@CacheEvict` can subclass it. Kotlin classes default to `final`.
- Renamed `zitadelUserId` parameter to `externalUserId`, fixed the `existsByOrganizationIdAndClerkUserId` typo, replaced `Member.Role` with `String` for the role parameter (apps using custom roles plug their own enum and call `.name`).

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 7: Create `OrganizationAutoConfiguration`

**Files:**
- Create: `src/main/kotlin/org/granchi/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt`
- Create: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/OrganizationAutoConfigurationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.granchi.saasstarter.organization.OrganizationService
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import strikt.api.expectThat
import strikt.assertions.hasSize

class OrganizationAutoConfigurationTest {

    @Test
    fun `autoconfig class is loaded`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrganizationAutoConfiguration::class.java))
            .run { context ->
                val configs = context.getBeansOfType(OrganizationAutoConfiguration::class.java)
                expectThat(configs).hasSize(1)
            }
    }
}
```

(End-to-end wiring of `OrganizationService` requires a real DataSource; that's covered by the integration test in Task 8.)

- [ ] **Step 2: Run the test — fails to compile**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.OrganizationAutoConfigurationTest"
```

Expected: `Unresolved reference: OrganizationAutoConfiguration`.

- [ ] **Step 3: Write the autoconfig**

```kotlin
package org.granchi.saasstarter.autoconfigure

import org.granchi.saasstarter.lock.RedisLockService
import org.granchi.saasstarter.organization.MemberRepository
import org.granchi.saasstarter.organization.OrganizationRepository
import org.granchi.saasstarter.organization.OrganizationService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

/**
 * Wires the multi-tenant `Organization` / `Member` model.
 *
 * - `@EnableJdbcRepositories` makes Spring Data JDBC scan the starter's
 *   organization package, so [OrganizationRepository] and [MemberRepository]
 *   are registered as beans even though the consuming app's @SpringBootApplication
 *   only scans its own package.
 * - [OrganizationService] is declared as a bean (with @ConditionalOnMissingBean)
 *   so apps may override with their own subclass.
 */
@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.data.jdbc.repository.config.EnableJdbcRepositories"])
@EnableJdbcRepositories(basePackages = ["org.granchi.saasstarter.organization"])
class OrganizationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(value = [
        OrganizationRepository::class,
        MemberRepository::class,
        RedisLockService::class,
    ])
    fun organizationService(
        organizationRepository: OrganizationRepository,
        memberRepository: MemberRepository,
        lockService: RedisLockService,
    ): OrganizationService = OrganizationService(organizationRepository, memberRepository, lockService)
}
```

- [ ] **Step 4: Run the test — passes**

```bash
./gradlew test --tests "org.granchi.saasstarter.autoconfigure.OrganizationAutoConfigurationTest"
```

Expected: BUILD SUCCESSFUL.

---

### Task 8: Add starter integration test for `OrganizationService` against a real DB

**Files:**
- Create: `src/test/kotlin/org/granchi/saasstarter/organization/OrganizationServiceIntegrationTest.kt`
- Create: `src/main/resources/db/migration/saasstarter/V100__starter_baseline.sql`

This integration test sanity-checks the autoconfig + repository + Flyway migration loop, before the template gets it.

- [ ] **Step 1: Add Flyway + Postgres + Testcontainers test dependencies if missing**

In `gradle/libs.versions.toml`:

```toml
[versions]
flyway            = "11.0.0"
postgresql        = "42.7.4"

[libraries]
flyway-core                   = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgres               = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
postgresql                    = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
testcontainers-postgres       = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
```

In `build.gradle.kts`:

```kotlin
testImplementation(libs.flyway.core)
testImplementation(libs.flyway.postgres)
testImplementation(libs.postgresql)
testImplementation(libs.testcontainers.postgres)
testImplementation(libs.spring.boot.data.jdbc)  // Needed for ApplicationContextRunner with JDBC autoconfigs
```

(Use `libs.spring.boot.data.jdbc` if it's already declared in starter; else add it.)

- [ ] **Step 2: Write the migration**

Create `src/main/resources/db/migration/saasstarter/V100__starter_baseline.sql`:

```sql
-- V100__starter_baseline.sql — kotlin-saas-starter
-- Multi-tenant baseline: organizations and members.

CREATE TABLE organizations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    plan       VARCHAR(50)  NOT NULL DEFAULT 'starter',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE members (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    external_user_id VARCHAR(255) NOT NULL UNIQUE,
    role             VARCHAR(50)  NOT NULL DEFAULT 'MEMBER',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Critical: hit on every authenticated request via MemberRepository.findOrganizationIdByUserId
CREATE INDEX idx_members_external_user_id ON members(external_user_id);
CREATE INDEX idx_members_organization_id  ON members(organization_id);
```

- [ ] **Step 3: Write the integration test**

```kotlin
package org.granchi.saasstarter.organization

import org.granchi.saasstarter.tenant.TenantContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@Tag("integration")
@Testcontainers
@SpringBootTest(
    classes = [OrganizationServiceIntegrationTest.TestApp::class],
    properties = [
        "spring.flyway.locations=classpath:db/migration/saasstarter",
        "saasstarter.cache.enabled=false",  // No Redis in this test
    ],
)
class OrganizationServiceIntegrationTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication
    class TestApp

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("starter_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Test
    fun `Flyway migration creates organizations and members tables`() {
        val org = organizationRepository.save(Organization(name = "Acme", slug = "acme"))
        val member = memberRepository.save(
            Member(organizationId = org.id, externalUserId = "user-123")
        )
        expectThat(memberRepository.findByExternalUserId("user-123")?.id).isEqualTo(member.id)
    }

    @Test
    fun `findOrganizationIdByUserId returns the org for a member`() {
        val org = organizationRepository.save(Organization(name = "Beta", slug = "beta"))
        memberRepository.save(Member(organizationId = org.id, externalUserId = "user-456"))

        val resolvedOrgId = memberRepository.findOrganizationIdByUserId("user-456")
        expectThat(resolvedOrgId).isEqualTo(org.id)
    }
}
```

This test bootstraps a minimal Spring context with just the autoconfig classes needed. It does NOT exercise the cache (Redis is disabled) — that's the template's job.

- [ ] **Step 4: Run the integration test**

```bash
./gradlew test --tests "org.granchi.saasstarter.organization.OrganizationServiceIntegrationTest"
```

Expected: BUILD SUCCESSFUL. Both tests pass.

If Spring Data JDBC complains about the missing `RedisLockService` bean for `OrganizationService`, exclude it from this minimal test by adding `@SpringBootTest(... excludeAutoConfiguration = [OrganizationAutoConfiguration::class])` and asserting only on the repositories. But this conflicts with our goal of testing the autoconfig — better to add a stub `@Bean RedisLockService` inside `TestApp`:

```kotlin
@org.springframework.boot.autoconfigure.SpringBootApplication
class TestApp {
    @org.springframework.context.annotation.Bean
    fun redisLockService(): org.granchi.saasstarter.lock.RedisLockService =
        // Construct with a mocked RedisTemplate
        org.granchi.saasstarter.lock.RedisLockService(org.mockito.kotlin.mock())
}
```

- [ ] **Step 5: Commit Tasks 6, 7, 8**

```bash
git add src/main/kotlin/org/granchi/saasstarter/organization/OrganizationService.kt \
        src/main/kotlin/org/granchi/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt \
        src/main/resources/db/migration/saasstarter/V100__starter_baseline.sql \
        src/test/kotlin/org/granchi/saasstarter/autoconfigure/OrganizationAutoConfigurationTest.kt \
        src/test/kotlin/org/granchi/saasstarter/organization/OrganizationServiceIntegrationTest.kt \
        gradle/libs.versions.toml \
        build.gradle.kts
git commit -m "$(cat <<'EOF'
feat: add OrganizationService, OrganizationAutoConfiguration, V100 migration

- OrganizationService renamed parameters to externalUserId, fixed
  existsByOrganizationIdAndClerkUserId typo (now ...AndExternalUserId).
- OrganizationAutoConfiguration uses @EnableJdbcRepositories so consuming
  apps see the repositories despite different package scopes.
- V100__starter_baseline.sql under db/migration/saasstarter/ creates
  organizations and members tables with the renamed external_user_id column.
- Integration test against a real Postgres via Testcontainers verifies
  migration applies and repository works.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Register `OrganizationAutoConfiguration` in the imports file

**Files:**
- Modify: `src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- Modify: `src/test/kotlin/org/granchi/saasstarter/autoconfigure/AutoConfigurationImportsTest.kt`

- [ ] **Step 1: Append the new FQCN**

Final file contents:

```
org.granchi.saasstarter.autoconfigure.SaasStarterAutoConfiguration
org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
org.granchi.saasstarter.autoconfigure.JobRunrAutoConfiguration
org.granchi.saasstarter.autoconfigure.RedisAutoConfiguration
org.granchi.saasstarter.autoconfigure.WebMvcAutoConfiguration
org.granchi.saasstarter.autoconfigure.OrganizationAutoConfiguration
```

- [ ] **Step 2: Add the corresponding test**

```kotlin
    @Test
    fun `imports file lists OrganizationAutoConfiguration`() {
        val resource = this::class.java.classLoader.getResource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        expectThat(resource).isNotNull()
        expectThat(resource!!.readText())
            .contains("org.granchi.saasstarter.autoconfigure.OrganizationAutoConfiguration")
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
feat: register OrganizationAutoConfiguration via AutoConfiguration.imports

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: Publish `0.7.0-SNAPSHOT` to mavenLocal

- [ ] **Step 1: Publish**

```bash
./gradlew publishToMavenLocal -Pversion=0.7.0-SNAPSHOT
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verify the JAR contains the migration file**

```bash
unzip -l ~/.m2/repository/org/granchi/kotlin-saas-starter/0.7.0-SNAPSHOT/kotlin-saas-starter-0.7.0-SNAPSHOT.jar \
       | grep V100__starter_baseline
```

Expected: a line listing `db/migration/saasstarter/V100__starter_baseline.sql`.

---

### Task 11: (Reserved) — see Task 12.

---

### Task 12: (Reserved) — starter side complete.

---

### Task 13: Renumber template's Flyway migrations

All template tasks happen inside `/var/home/serandel/Projects/kotlin-saas-template`.

**Files:**
- Rename: `app/src/main/resources/db/migration/V1__init.sql` → `V200__app_init.sql`
- Rename: `app/src/main/resources/db/migration/V2__jobs.sql` → `V201__jobs.sql`
- Rename: `app/src/main/resources/db/migration/V3__analysis.sql` → `V202__analysis.sql`

The renumbering ensures the starter's `V100__starter_baseline.sql` runs BEFORE the template's app-specific migrations, satisfying the FK from `subscriptions(organization_id)` to `organizations(id)`.

- [ ] **Step 1: Rename the migration files**

```bash
git mv app/src/main/resources/db/migration/V1__init.sql      app/src/main/resources/db/migration/V200__app_init.sql
git mv app/src/main/resources/db/migration/V2__jobs.sql      app/src/main/resources/db/migration/V201__jobs.sql
git mv app/src/main/resources/db/migration/V3__analysis.sql  app/src/main/resources/db/migration/V202__analysis.sql
```

- [ ] **Step 2: Don't commit yet** — Tasks 14-16 finish the migration changes.

---

### Task 14: Strip `organizations` and `members` from `V200__app_init.sql`

**Files:**
- Modify: `app/src/main/resources/db/migration/V200__app_init.sql`

- [ ] **Step 1: Replace the file content**

Replace the contents of `V200__app_init.sql` with the following — `subscriptions` only:

```sql
-- V200__app_init.sql
-- App-specific tables. Multi-tenant baseline (organizations, members) lives
-- in kotlin-saas-starter's V100__starter_baseline.sql.

CREATE TABLE subscriptions (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID         NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    stripe_customer_id     VARCHAR(255) NOT NULL UNIQUE,
    stripe_subscription_id VARCHAR(255),
    plan                   VARCHAR(50)  NOT NULL DEFAULT 'STARTER',
    status                 VARCHAR(50)  NOT NULL DEFAULT 'TRIALING',
    current_period_end     TIMESTAMPTZ,
    cancel_at_period_end   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_stripe_customer_id     ON subscriptions(stripe_customer_id);
CREATE INDEX idx_subscriptions_stripe_subscription_id ON subscriptions(stripe_subscription_id);
```

`subscriptions` is moved to starter in Plan 7. The `stripe_customer_id` / `stripe_subscription_id` column names will be renamed at that time.

---

### Task 15: Configure Flyway to scan both migration locations

**Files:**
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Update Flyway locations**

Find the `spring.flyway` block and change `locations` from a single classpath to a list:

```yaml
spring:
  flyway:
    enabled: true
    locations:
      - classpath:db/migration
      - classpath:db/migration/saasstarter
```

The order of locations does NOT determine migration execution order — Flyway sorts ALL migrations across all locations by version number. With our scheme, `V100__starter_baseline.sql` (starter location) runs before `V200__app_init.sql` (template location).

---

### Task 16: Bump starter version and reset local DB

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Bump starter version**

```toml
kotlin-saas-starter = "0.7.0-SNAPSHOT"
```

- [ ] **Step 2: Reset local Postgres volume**

The renumbered migrations conflict with Flyway's prior history (which expected `V1__init.sql`). Wipe the volume:

```bash
docker compose down -v
docker compose up -d
```

Expected: clean Postgres / Redis / Zitadel containers running.

- [ ] **Step 3: Refresh Gradle dependencies**

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --refresh-dependencies | grep kotlin-saas-starter
```

Expected: `org.granchi:kotlin-saas-starter:0.7.0-SNAPSHOT`.

---

### Task 17: Update `MemberTenantResolver` to use starter's `MemberRepository`

**Files:**
- Modify: `app/src/main/kotlin/org/granchi/saastemplate/config/MemberTenantResolver.kt`

- [ ] **Step 1: Update the imports**

```kotlin
package org.granchi.saastemplate.config

import org.granchi.saasstarter.organization.MemberRepository
import org.granchi.saasstarter.tenant.TenantResolver
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MemberTenantResolver(
    private val memberRepository: MemberRepository,
) : TenantResolver {

    override fun resolveTenantId(userId: String): UUID? =
        memberRepository.findOrganizationIdByUserId(userId)
}
```

The class itself doesn't change — only the package the import is from. The repository's `findOrganizationIdByUserId` method signature is unchanged.

---

### Task 18: Update `OrganizationController` to use starter classes; delete template's organization package

**Files:**
- Modify: `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationController.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/organization/Organization.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationRepository.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationService.kt`
- Delete: `app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationValidations.kt`

- [ ] **Step 1: Update `OrganizationController.kt` imports**

Add the necessary imports:

```kotlin
package org.granchi.saastemplate.organization

import org.granchi.saasstarter.organization.OrganizationService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/organization")
class OrganizationController(private val organizationService: OrganizationService) {
    // method bodies unchanged
}
```

- [ ] **Step 2: Delete the four moved files**

```bash
git rm app/src/main/kotlin/org/granchi/saastemplate/organization/Organization.kt \
       app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationRepository.kt \
       app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationService.kt \
       app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationValidations.kt
```

- [ ] **Step 3: Update template's `BillingService.kt` if it references these types**

```bash
grep -n "import org.granchi.saastemplate.organization\." app/src/main/kotlin/org/granchi/saastemplate/billing/*.kt 2>/dev/null
```

If any imports point to the deleted package, change them to `import org.granchi.saasstarter.organization.<Type>`. The most likely candidate is `BillingService` if it joins `Subscription` to `Organization` — verify and update.

- [ ] **Step 4: Verify the project compiles**

```bash
./gradlew :app:compileKotlin
```

Expected: BUILD SUCCESSFUL. If it fails on Thymeleaf templates referencing `Member.zitadelUserId`, those need updating too — search:

```bash
grep -rn "zitadelUserId" app/src/main/resources/templates 2>/dev/null
```

If matches found, update to `externalUserId`.

---

### Task 19: Add a smoke test confirming starter wiring + tenant flow

**Files:**
- Create: `app/src/test/kotlin/org/granchi/saastemplate/integration/OrganizationAutoConfigSmokeTest.kt`

- [ ] **Step 1: Write the smoke test**

```kotlin
package org.granchi.saastemplate.integration

import org.granchi.saasstarter.organization.Member
import org.granchi.saasstarter.organization.MemberRepository
import org.granchi.saasstarter.organization.Organization
import org.granchi.saasstarter.organization.OrganizationRepository
import org.granchi.saasstarter.organization.OrganizationService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@Tag("integration")
@SpringBootTest
class OrganizationAutoConfigSmokeTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var organizationService: OrganizationService

    @Test
    fun `OrganizationRepository, MemberRepository, and OrganizationService are wired from starter`() {
        expectThat(context.getBeansOfType(OrganizationRepository::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(MemberRepository::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(OrganizationService::class.java)).hasSize(1)
    }

    @Test
    fun `member tenant resolution finds organization for an external user`() {
        val org = organizationRepository.save(Organization(name = "Smoke", slug = "smoke-test"))
        memberRepository.save(Member(organizationId = org.id, externalUserId = "smoke-user"))

        val resolvedOrgId = memberRepository.findOrganizationIdByUserId("smoke-user")
        expectThat(resolvedOrgId).isEqualTo(org.id)
    }
}
```

- [ ] **Step 2: Run the smoke test**

```bash
./gradlew :app:integrationTest --tests "org.granchi.saastemplate.integration.OrganizationAutoConfigSmokeTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full test suite**

```bash
./gradlew :app:test :app:integrationTest
```

Expected: BUILD SUCCESSFUL. `TenantIsolationTest` continues to pass — it imports `Organization` and `OrganizationRepository`, but updated imports in Task 18 (template's controllers/configs) propagate; the test file likely needs imports updated too:

```bash
grep -rn "org.granchi.saastemplate.organization" app/src/test --include="*.kt"
```

If matches surface, update them to `org.granchi.saasstarter.organization`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml \
        app/src/main/resources/application.yml \
        app/src/main/resources/db/migration/V200__app_init.sql \
        app/src/main/resources/db/migration/V201__jobs.sql \
        app/src/main/resources/db/migration/V202__analysis.sql \
        app/src/main/kotlin/org/granchi/saastemplate/config/MemberTenantResolver.kt \
        app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationController.kt \
        app/src/main/kotlin/org/granchi/saastemplate/organization/Organization.kt \
        app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationRepository.kt \
        app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationService.kt \
        app/src/main/kotlin/org/granchi/saastemplate/organization/OrganizationValidations.kt \
        app/src/test/kotlin/org/granchi/saastemplate/integration/OrganizationAutoConfigSmokeTest.kt

# If billing or test imports also changed, add them too:
git add app/src/main/kotlin/org/granchi/saastemplate/billing/ \
        app/src/test/kotlin/org/granchi/saastemplate/integration/TenantIsolationTest.kt 2>/dev/null

git commit -m "$(cat <<'EOF'
refactor: delegate multi-tenant model to kotlin-saas-starter 0.7.0

- Organization, Member, OrganizationRepository, MemberRepository,
  OrganizationValidations, and OrganizationService now live in the starter.
- Member.zitadelUserId is renamed to externalUserId across code, schema, and
  cache keys.
- Flyway migrations renumbered (V1->V200, V2->V201, V3->V202) so starter's
  V100__starter_baseline.sql runs first; subscriptions table FK to
  organizations now resolves correctly.
- Template keeps OrganizationController and MemberTenantResolver; smoke test
  verifies starter beans are wired and tenant resolution works end-to-end.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

### Task 20: Open release PRs and finalize

- [ ] **Step 1: Push starter branch and open PR**

In `/var/home/serandel/Projects/kotlin-saas-starter`:

```bash
git push -u origin <branch-name>
gh pr create --title "feat: add multi-tenant model (Organization, Member, OrganizationService)" \
             --body "$(cat <<'EOF'
## Summary

- Adds Organization and Member entities (with externalUserId), OrganizationRepository, MemberRepository, OrganizationValidations, and OrganizationService.
- Adds MemberRole interface with DefaultMemberRole enum (OWNER/ADMIN/MEMBER) for app extensibility.
- OrganizationAutoConfiguration uses @EnableJdbcRepositories so Spring Data JDBC discovers the repositories despite app package scoping.
- RedisLockService is now declared by RedisAutoConfiguration (drops @Service).
- Ships V100__starter_baseline.sql under db/migration/saasstarter/.
- Integration test against real Postgres verifies migration applies and findOrganizationIdByUserId works.

Implements Plan 6 of docs/superpowers/specs/2026-05-07-starter-template-split-design.md.

## Breaking changes

None for code that hasn't already been adopting starter classes — these classes are new in the starter.

## Test plan

- [x] OrganizationServiceIntegrationTest verifies Flyway migration + repository + entity round-trip
- [x] OrganizationAutoConfigurationTest, MemberRoleTest cover wiring and types
- [x] AutoConfigurationImportsTest covers the new entry
- [x] Template OrganizationAutoConfigSmokeTest passes against 0.7.0-SNAPSHOT (separate PR)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 2: Wait for CI, merge PRs**

After release-please's PR merges, `0.7.0` is published.

- [ ] **Step 3: Bump template to released `0.7.0`**

```bash
sed -i 's/kotlin-saas-starter = "0.7.0-SNAPSHOT"/kotlin-saas-starter = "0.7.0"/' \
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
build: bump kotlin-saas-starter to 0.7.0

Replaces 0.7.0-SNAPSHOT with the released version (Plan 6: multi-tenant model).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push
```

---

## Done When

- `kotlin-saas-starter` 0.7.0 is published with `Organization`, `Member`, repositories, validations, and `OrganizationService`.
- Template depends on `0.7.0`. The `app/src/main/kotlin/org/granchi/saastemplate/organization/` directory contains only `OrganizationController.kt`.
- `OrganizationAutoConfigSmokeTest` and `TenantIsolationTest` both pass.
- Local Postgres volume is fresh; Flyway successfully applies V100 (starter) → V200 (template) → V201 (jobs) → V202 (analysis).
- Field rename complete: no `zitadelUserId` / `zitadel_user_id` / `clerkUserId` references remain in code or templates.
- Bug fix: `existsByOrganizationIdAndExternalUserId` is correctly named and called.
- Both repos' commits are pushed.
