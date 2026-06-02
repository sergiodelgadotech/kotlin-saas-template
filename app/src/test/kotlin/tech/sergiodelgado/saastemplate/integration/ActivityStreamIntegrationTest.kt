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
            TestingAuthenticationToken("actor-user", "")
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
            TestingAuthenticationToken("actor-user", "")
        TenantContext.set(orgA.id)
        organizationService.inviteMember("invited-user-${System.nanoTime()}")
        TenantContext.clear()

        verify(exactly = 0) { emitterB.send(any<SseEmitter.SseEventBuilder>()) }
    }
}
