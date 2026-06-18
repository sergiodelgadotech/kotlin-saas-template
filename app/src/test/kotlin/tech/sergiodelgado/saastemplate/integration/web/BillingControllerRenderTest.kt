package tech.sergiodelgado.saastemplate.integration.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import java.time.Instant
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test", "local")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
    ],
)
class BillingControllerRenderTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        val devOrgId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean(relaxed = true)
    lateinit var billingService: BillingService

    @MockkBean
    lateinit var tenantResolver: TenantResolver

    @BeforeEach
    fun setup() {
        every { tenantResolver.resolveTenantId(any()) } returns devOrgId
    }

    @Test
    fun `billing renders no-subscription alert when subscription is null`() {
        every { billingService.currentSubscription() } returns null

        mvc.perform(get("/billing"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("No active subscription")))
            .andExpect(content().string(not(containsString("Manage Subscription"))))
    }

    @Test
    fun `billing renders Starter as free tier with features and no status badge`() {
        every { billingService.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = devOrgId,
            externalCustomerId = "cus_test",
            plan = "STARTER",
            status = SubscriptionStatus.ACTIVE,
        )

        mvc.perform(get("/billing"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("€0")))
            .andExpect(content().string(containsString("forever")))
            .andExpect(content().string(containsString("Up to 5 users")))
            .andExpect(content().string(not(containsString("ACTIVE"))))
            .andExpect(content().string(not(containsString("TRIALING"))))
            .andExpect(content().string(not(containsString("Manage Subscription"))))
            .andExpect(content().string(containsString("Upgrade to Pro")))
    }

    @Test
    fun `billing does not show status badge or renewal date for Starter trialing`() {
        every { billingService.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = devOrgId,
            externalCustomerId = "cus_test",
            plan = "STARTER",
            status = SubscriptionStatus.TRIALING,
            currentPeriodEnd = Instant.parse("2026-12-31T23:59:59Z"),
        )

        mvc.perform(get("/billing"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("€0")))
            .andExpect(content().string(not(containsString("TRIALING"))))
            .andExpect(content().string(not(containsString("Renews"))))
    }

    // ── CSRF regression ───────────────────────────────────────────────────────

    /**
     * Regression for the Starter→Pro upgrade bug:
     * POST /webhooks/stripe was blocked by CSRF (403) before the fix.
     * After exempting /webhooks/** from CSRF, the request reaches the controller and
     * returns 400 (invalid Stripe signature) — not 403.
     */
    @Test
    fun `POST to webhook endpoint without CSRF token returns 400 not 403`() {
        mvc.perform(
            post("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=1,v1=bogus")
                .content("""{"id":"evt_test","type":"customer.subscription.created"}""")
        )
            .andExpect(status().isBadRequest)   // 400 = reached controller, bad signature
    }

    // ── syncFromStripe on success return ──────────────────────────────────────

    @Test
    fun `GET billing with success=true calls syncFromStripe instead of currentSubscription`() {
        val proSub = Subscription(
            id = UUID.randomUUID(),
            organizationId = devOrgId,
            externalCustomerId = "cus_test",
            plan = "PRO",
            status = SubscriptionStatus.ACTIVE,
        )
        every { billingService.syncFromStripe() } returns proSub

        mvc.perform(get("/billing").param("success", "true"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Pro")))

        verify(exactly = 1) { billingService.syncFromStripe() }
        verify(exactly = 0) { billingService.currentSubscription() }
    }

    @Test
    fun `GET billing without success param does not call syncFromStripe`() {
        every { billingService.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = devOrgId,
            externalCustomerId = "cus_test",
            plan = "STARTER",
            status = SubscriptionStatus.TRIALING,
        )

        mvc.perform(get("/billing"))
            .andExpect(status().isOk)

        verify(exactly = 0) { billingService.syncFromStripe() }
        verify(exactly = 1) { billingService.currentSubscription() }
    }
}
