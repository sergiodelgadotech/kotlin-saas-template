# Domain Event Bus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire an in-process domain event bus so the dashboard SSE activity stream shows real-time events — starting with "member invited" as the first producer.

**Architecture:** `OrganizationService.inviteMember` publishes a `MemberInvitedEvent` via Spring's `ApplicationEventPublisher`. An `ActivityStreamService` in the template holds active `SseEmitter` instances keyed by org ID and routes events to the right emitters via `@EventListener`. `DashboardController.activityStream` registers the emitter without reading `TenantContext` directly.

**Tech Stack:** Spring `ApplicationEventPublisher` + `@EventListener`, `SseEmitter` (Spring MVC), Mockk (tests), Testcontainers (Postgres + Redis for integration test)

---

## File Map

**Starter (`../kotlin-saas-starter`):**
- Create: `src/main/kotlin/tech/sergiodelgado/saasstarter/organization/MemberInvitedEvent.kt`
- Modify: `src/main/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationService.kt` — add `ApplicationEventPublisher`, publish after save
- Modify: `src/main/kotlin/tech/sergiodelgado/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt` — pass `ApplicationEventPublisher` to `OrganizationService`
- Modify: `src/test/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationServiceTest.kt` — add publisher mock + event publication test

**Template (`app/`):**
- Create: `app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamService.kt`
- Create: `app/src/test/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamServiceTest.kt`
- Modify: `app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/DashboardController.kt` — inject `ActivityStreamService`, remove `emitter.complete()`
- Create: `app/src/test/kotlin/tech/sergiodelgado/saastemplate/integration/ActivityStreamIntegrationTest.kt`

---

### Task 1: Commit spec and move issue to In Progress

**Files:**
- No code changes

- [ ] **Step 1: Move GitHub issue to In Progress**

```bash
gh issue edit 57 --add-label "in-progress" 2>/dev/null || true
gh project item-edit --id $(gh project item-list 1 --owner sergiodelgadotech --format json | jq -r '.items[] | select(.content.number == 57) | .id') --field-id $(gh project field-list 1 --owner sergiodelgadotech --format json | jq -r '.fields[] | select(.name == "Status") | .id') --project-id $(gh project list --owner sergiodelgadotech --format json | jq -r '.projects[] | select(.number == 1) | .id') --single-select-option-id $(gh project field-list 1 --owner sergiodelgadotech --format json | jq -r '.fields[] | select(.name == "Status") | .options[] | select(.name == "In Progress") | .id') 2>/dev/null || true
```

- [ ] **Step 2: Commit the spec**

Working directory: `kotlin-saas-template`

```bash
git add docs/superpowers/specs/2026-06-02-domain-event-bus-design.md
git commit -m "docs: add domain event bus spec (#57)"
```

---

### Task 2: Add `MemberInvitedEvent` to the starter

**Files:**
- Create: `src/main/kotlin/tech/sergiodelgado/saasstarter/organization/MemberInvitedEvent.kt`

Working directory: `../kotlin-saas-starter`

- [ ] **Step 1: Create the event class**

Create `src/main/kotlin/tech/sergiodelgado/saasstarter/organization/MemberInvitedEvent.kt`:

```kotlin
package tech.sergiodelgado.saasstarter.organization

import java.util.UUID

data class MemberInvitedEvent(
    val organizationId: UUID,
    val memberId: UUID,
    val invitedExternalUserId: String,
    val actorExternalUserId: String,
)
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/tech/sergiodelgado/saasstarter/organization/MemberInvitedEvent.kt
git commit -m "feat(org): add MemberInvitedEvent domain event"
```

---

### Task 3: Update `OrganizationService` to publish `MemberInvitedEvent` (TDD)

**Files:**
- Modify: `src/test/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationServiceTest.kt`
- Modify: `src/main/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationService.kt`
- Modify: `src/main/kotlin/tech/sergiodelgado/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt`

Working directory: `../kotlin-saas-starter`

- [ ] **Step 1: Add `ApplicationEventPublisher` mock to the test and a new failing test**

Replace the top of `OrganizationServiceTest.kt` (the fields and constructor line):

Old:
```kotlin
    private val orgRepo = mockk<OrganizationRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val lockService = mockk<RedisLockService>()
    private val service = OrganizationService(orgRepo, memberRepo, lockService)
```

New:
```kotlin
    private val orgRepo = mockk<OrganizationRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val lockService = mockk<RedisLockService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = OrganizationService(orgRepo, memberRepo, lockService, eventPublisher)
```

Add this import at the top of the file:
```kotlin
import org.springframework.context.ApplicationEventPublisher
```

Add this test after `inviteMember throws when user is already a member`:
```kotlin
    @Test
    fun `inviteMember publishes MemberInvitedEvent after saving`() {
        val userId = "user-event-test"
        val savedMember = Member(id = UUID.randomUUID(), organizationId = orgId, externalUserId = userId)
        every { lockService.withLock<Member>(any(), any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[2] as Function0<Member>).invoke()
        }
        every { memberRepo.existsByOrganizationIdAndExternalUserId(orgId, userId) } returns false
        every { memberRepo.save(any()) } returns savedMember

        service.inviteMember(userId)

        verify {
            eventPublisher.publishEvent(
                MemberInvitedEvent(
                    organizationId = orgId,
                    memberId = savedMember.id,
                    invitedExternalUserId = userId,
                    actorExternalUserId = any(),
                )
            )
        }
    }
```

- [ ] **Step 2: Run the test suite to confirm the new test fails and existing tests fail due to constructor mismatch**

```bash
./gradlew :test --tests "tech.sergiodelgado.saasstarter.organization.OrganizationServiceTest"
```

Expected: FAILED — compilation error (constructor has 3 params, we pass 4) or test failure.

- [ ] **Step 3: Add `ApplicationEventPublisher` to `OrganizationService` and publish the event**

Full updated `src/main/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationService.kt`:

```kotlin
package tech.sergiodelgado.saasstarter.organization

import tech.sergiodelgado.saasstarter.lock.RedisLockService
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import tech.sergiodelgado.saasstarter.web.NotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
open class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val memberRepository: MemberRepository,
    private val lockService: RedisLockService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    fun current(): Organization =
        organizationRepository.findById(TenantContext.get()).orElseThrow {
            NotFoundException("Organization not found")
        }

    fun members(): List<Member> =
        memberRepository.findByOrganizationId(TenantContext.get())

    fun inviteMember(externalUserId: String, role: String = DefaultMemberRole.MEMBER.name): Member {
        val orgId = TenantContext.get()
        return lockService.withLock("invite:$orgId:$externalUserId") {
            check(!memberRepository.existsByOrganizationIdAndExternalUserId(orgId, externalUserId)) {
                "User is already a member of this organization"
            }
            val saved = memberRepository.save(
                Member(organizationId = orgId, externalUserId = externalUserId, role = role)
            )
            val actor = SecurityContextHolder.getContext().authentication?.name ?: "system"
            applicationEventPublisher.publishEvent(
                MemberInvitedEvent(
                    organizationId = orgId,
                    memberId = saved.id,
                    invitedExternalUserId = externalUserId,
                    actorExternalUserId = actor,
                )
            )
            saved
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
        val org = current()
        val updated = org.copy(name = name)
        updated._new = false
        return organizationRepository.save(updated)
    }
}
```

- [ ] **Step 4: Update `OrganizationAutoConfiguration` to pass `ApplicationEventPublisher`**

In `src/main/kotlin/tech/sergiodelgado/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt`, update the `organizationService` bean factory method:

Old:
```kotlin
        @Bean
        @ConditionalOnMissingBean
        fun organizationService(
            organizationRepository: OrganizationRepository,
            memberRepository: MemberRepository,
            lockService: RedisLockService,
        ): OrganizationService =
            OrganizationService(organizationRepository, memberRepository, lockService)
```

New:
```kotlin
        @Bean
        @ConditionalOnMissingBean
        fun organizationService(
            organizationRepository: OrganizationRepository,
            memberRepository: MemberRepository,
            lockService: RedisLockService,
            applicationEventPublisher: ApplicationEventPublisher,
        ): OrganizationService =
            OrganizationService(organizationRepository, memberRepository, lockService, applicationEventPublisher)
```

Add the import at the top of the file:
```kotlin
import org.springframework.context.ApplicationEventPublisher
```

- [ ] **Step 5: Run all starter tests**

```bash
./gradlew :test
```

Expected: BUILD SUCCESSFUL, all tests pass including the new `inviteMember publishes MemberInvitedEvent` test.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationService.kt \
        src/main/kotlin/tech/sergiodelgado/saasstarter/autoconfigure/OrganizationAutoConfiguration.kt \
        src/test/kotlin/tech/sergiodelgado/saasstarter/organization/OrganizationServiceTest.kt
git commit -m "feat(org): publish MemberInvitedEvent from inviteMember"
```

---

### Task 4: Implement `ActivityStreamService` (TDD)

**Files:**
- Create: `app/src/test/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamServiceTest.kt`
- Create: `app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamService.kt`

Working directory: `kotlin-saas-template`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamServiceTest.kt`:

```kotlin
package tech.sergiodelgado.saastemplate.dashboard

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tech.sergiodelgado.saasstarter.organization.MemberInvitedEvent
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.util.UUID

class ActivityStreamServiceTest {

    private val service = ActivityStreamService()

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `on sends event only to the matching org's emitters`() {
        val orgA = UUID.randomUUID()
        val orgB = UUID.randomUUID()
        val emitterA = mockk<SseEmitter>(relaxed = true)
        val emitterB = mockk<SseEmitter>(relaxed = true)

        TenantContext.set(orgA)
        service.register(emitterA)
        TenantContext.clear()

        TenantContext.set(orgB)
        service.register(emitterB)
        TenantContext.clear()

        service.on(
            MemberInvitedEvent(
                organizationId = orgA,
                memberId = UUID.randomUUID(),
                invitedExternalUserId = "new-user",
                actorExternalUserId = "actor",
            )
        )

        verify { emitterA.send(any<SseEmitter.SseEventBuilder>()) }
        verify(exactly = 0) { emitterB.send(any()) }
    }

    @Test
    fun `on does nothing when no emitters are registered for the org`() {
        val orgId = UUID.randomUUID()
        service.on(
            MemberInvitedEvent(
                organizationId = orgId,
                memberId = UUID.randomUUID(),
                invitedExternalUserId = "new-user",
                actorExternalUserId = "actor",
            )
        )
        // No exception = pass
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails (class not found)**

```bash
./gradlew :app:test --tests "tech.sergiodelgado.saastemplate.dashboard.ActivityStreamServiceTest"
```

Expected: FAILED — compilation error, `ActivityStreamService` does not exist.

- [ ] **Step 3: Implement `ActivityStreamService`**

Create `app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamService.kt`:

```kotlin
package tech.sergiodelgado.saastemplate.dashboard

import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tech.sergiodelgado.saasstarter.organization.MemberInvitedEvent
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class ActivityStreamService {

    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    fun register(emitter: SseEmitter) {
        val orgId = TenantContext.get()
        val list = emitters.getOrPut(orgId) { CopyOnWriteArrayList() }
        list.add(emitter)
        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
    }

    // Fires only after the transaction commits — guarantees no SSE event is sent
    // for a member that was never actually persisted (e.g. transaction rolled back).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberInvitedEvent) {
        val list = emitters[event.organizationId] ?: return
        val message = SseEmitter.event()
            .data(
                """<p class="text-sm">${event.invitedExternalUserId} was invited to the organization</p>""",
                MediaType.TEXT_HTML
            )
        val toRemove = mutableListOf<SseEmitter>()
        list.forEach { emitter ->
            try {
                emitter.send(message)
            } catch (_: Exception) {
                toRemove.add(emitter)
            }
        }
        list.removeAll(toRemove.toSet())
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:test --tests "tech.sergiodelgado.saastemplate.dashboard.ActivityStreamServiceTest"
```

Expected: BUILD SUCCESSFUL, both tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamService.kt \
        app/src/test/kotlin/tech/sergiodelgado/saastemplate/dashboard/ActivityStreamServiceTest.kt
git commit -m "feat(dashboard): add ActivityStreamService for SSE fan-out"
```

---

### Task 5: Wire `DashboardController` to `ActivityStreamService`

**Files:**
- Modify: `app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/DashboardController.kt`

Working directory: `kotlin-saas-template`

- [ ] **Step 1: Update `DashboardController`**

Replace the full file content of `app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/DashboardController.kt`:

```kotlin
package tech.sergiodelgado.saastemplate.dashboard

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.organization.OrganizationService

@Controller
@RequestMapping("/dashboard")
class DashboardController(
    private val organizationService: OrganizationService,
    private val billingService: BillingService,
    private val activityStreamService: ActivityStreamService,
) {
    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        model.addAttribute("subscription", billingService.currentSubscription())
        return "dashboard/index"
    }

    @GetMapping("/stats")
    fun stats(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        return "fragments/dashboard-stats"
    }

    @GetMapping("/activity-stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun activityStream(): SseEmitter {
        val emitter = SseEmitter(60_000L)
        activityStreamService.register(emitter)
        return emitter
    }
}
```

- [ ] **Step 2: Run architecture tests to confirm no violations**

```bash
./gradlew :app:architectureTest
```

Expected: BUILD SUCCESSFUL — `DashboardController` does not import `TenantContext`, does not import any `Repository`, no HTTP imports in services.

- [ ] **Step 3: Run full unit test suite**

```bash
./gradlew :app:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/tech/sergiodelgado/saastemplate/dashboard/DashboardController.kt
git commit -m "feat(dashboard): wire ActivityStreamService into activityStream endpoint"
```

---

### Task 6: Integration test — tenant isolation end-to-end

**Files:**
- Create: `app/src/test/kotlin/tech/sergiodelgado/saastemplate/integration/ActivityStreamIntegrationTest.kt`

Working directory: `kotlin-saas-template`

This test uses the real Spring context (Testcontainers Postgres via `application-test.yml` + Redis container), the real `ApplicationEventPublisher`, and mock `SseEmitter` instances to assert routing and isolation.

- [ ] **Step 1: Write the integration test**

Create `app/src/test/kotlin/tech/sergiodelgado/saastemplate/integration/ActivityStreamIntegrationTest.kt`:

```kotlin
package tech.sergiodelgado.saastemplate.integration

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import tech.sergiodelgado.saastemplate.dashboard.ActivityStreamService
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import tech.sergiodelgado.saasstarter.organization.OrganizationService
import tech.sergiodelgado.saasstarter.tenant.TenantContext

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class ActivityStreamIntegrationTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var organizationService: OrganizationService

    @Autowired
    private lateinit var activityStreamService: ActivityStreamService

    @AfterEach
    fun cleanUp() {
        TenantContext.clear()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `inviteMember event reaches the correct org emitter`() {
        val org = organizationRepository.save(
            Organization(name = "EventOrg", slug = "event-org-${System.nanoTime()}")
        )
        val emitter = mockk<SseEmitter>(relaxed = true)

        TenantContext.set(org.id)
        activityStreamService.register(emitter)
        TenantContext.clear()

        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("actor-user", null)
        TenantContext.set(org.id)
        organizationService.inviteMember("invited-user-${System.nanoTime()}")
        TenantContext.clear()

        verify { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `inviteMember event does not reach a different org emitter`() {
        val orgA = organizationRepository.save(
            Organization(name = "OrgA", slug = "org-a-${System.nanoTime()}")
        )
        val orgB = organizationRepository.save(
            Organization(name = "OrgB", slug = "org-b-${System.nanoTime()}")
        )
        val emitterB = mockk<SseEmitter>(relaxed = true)

        TenantContext.set(orgB.id)
        activityStreamService.register(emitterB)
        TenantContext.clear()

        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("actor-user", null)
        TenantContext.set(orgA.id)
        organizationService.inviteMember("invited-user-${System.nanoTime()}")
        TenantContext.clear()

        verify(exactly = 0) { emitterB.send(any()) }
    }
}
```

- [ ] **Step 2: Run the integration tests**

```bash
./gradlew :app:integrationTest
```

Expected: BUILD SUCCESSFUL, both `ActivityStreamIntegrationTest` tests pass alongside existing integration tests.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/kotlin/tech/sergiodelgado/saastemplate/integration/ActivityStreamIntegrationTest.kt
git commit -m "test(dashboard): integration test for SSE tenant isolation"
```

---

### Task 7: Final verification and plan doc commit

Working directory: `kotlin-saas-template`

- [ ] **Step 1: Run full check in the template**

```bash
./gradlew :app:test :app:architectureTest :app:integrationTest
```

Expected: BUILD SUCCESSFUL across all three task groups.

- [ ] **Step 2: Commit the plan doc**

```bash
git add docs/superpowers/plans/2026-06-02-domain-event-bus.md
git commit -m "docs: add domain event bus implementation plan (#57)"
```
