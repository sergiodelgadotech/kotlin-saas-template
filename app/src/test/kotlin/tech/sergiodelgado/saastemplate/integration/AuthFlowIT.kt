package tech.sergiodelgado.saastemplate.integration

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationService
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        val orgId: UUID = UUID.randomUUID()
    }

    @Autowired private lateinit var mockMvc: MockMvc

    // TenantInterceptor resolves the tenant from the authenticated user's ID.
    // Without a mock returning a valid UUID, it would send 403 for protected routes.
    @MockkBean
    private lateinit var tenantResolver: TenantResolver

    // DashboardController calls organizationService.current() and billingService.currentSubscription().
    // Mock these so the context renders without hitting the database.
    @MockkBean(relaxed = true)
    private lateinit var organizationService: OrganizationService

    @MockkBean(relaxed = true)
    private lateinit var billingService: BillingService

    @Test
    fun `unauthenticated request to dashboard redirects to sign-in`() {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sign-in"))
    }

    @Test
    fun `GET sign-in redirects to Zitadel authorize`() {
        mockMvc.perform(get("/sign-in"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/oauth2/authorization/zitadel"))
    }

    @Test
    fun `GET sign-up redirects to Zitadel authorize`() {
        mockMvc.perform(get("/sign-up"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/oauth2/authorization/zitadel"))
    }

    @Test
    fun `authenticated user can access dashboard`() {
        every { tenantResolver.resolveTenantId(any()) } returns orgId
        every { organizationService.current() } returns Organization(
            id = orgId,
            name = "Test Org",
            slug = "test-org",
        )

        mockMvc.perform(
            get("/dashboard").with(oidcLogin().idToken { it.subject("test-user-id") })
        ).andExpect(status().isOk)
    }

    @Test
    fun `POST sign-out with CSRF redirects`() {
        mockMvc.perform(
            post("/sign-out")
                .with(oidcLogin())
                .with(csrf())
        ).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `POST sign-out without CSRF token returns 403`() {
        mockMvc.perform(
            post("/sign-out").with(oidcLogin())
        ).andExpect(status().isForbidden)
    }
}
