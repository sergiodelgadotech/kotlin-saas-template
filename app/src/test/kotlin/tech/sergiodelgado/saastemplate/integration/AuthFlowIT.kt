package tech.sergiodelgado.saastemplate.integration

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
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
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
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

    // OnboardingGateInterceptor and NavModelAdvice read member/subscription directly.
    // Relaxed so NavModelAdvice.findByExternalUserId() returns null (renders "?" initials) without
    // needing an explicit stub in every test.
    @MockkBean(relaxed = true)
    private lateinit var memberRepository: MemberRepository

    @MockkBean(relaxed = true)
    private lateinit var subscriptionRepository: SubscriptionRepository

    // DashboardController calls organizationService.current() and billingService.currentSubscription().
    // Mock these so the context renders without hitting the database.
    @MockkBean(relaxed = true)
    private lateinit var organizationService: OrganizationService

    @MockkBean(relaxed = true)
    private lateinit var billingService: BillingService

    @Test
    fun `unauthenticated request to dashboard redirects to sign-in`() {
        // anonymous() sets a non-null AnonymousAuthenticationToken so TestAutoAuthFilter skips,
        // but Spring Security still treats the request as unauthenticated.
        mockMvc.perform(get("/dashboard").with(anonymous()))
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
        every { memberRepository.findOrganizationIdByUserId(any()) } returns orgId.toString()
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
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
    fun `user without org is redirected to onboarding organization`() {
        every { memberRepository.findOrganizationIdByUserId(any()) } returns null

        mockMvc.perform(
            get("/dashboard").with(oidcLogin().idToken { it.subject("new-user") })
        ).andExpect(status().is3xxRedirection)
         .andExpect(redirectedUrl("/onboarding/organization"))
    }

    @Test
    fun `user with org but no subscription is redirected to onboarding plan`() {
        every { memberRepository.findOrganizationIdByUserId(any()) } returns orgId.toString()
        every { subscriptionRepository.findByOrganizationId(orgId) } returns null

        mockMvc.perform(
            get("/dashboard").with(oidcLogin().idToken { it.subject("partial-user") })
        ).andExpect(status().is3xxRedirection)
         .andExpect(redirectedUrl("/onboarding/plan"))
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
