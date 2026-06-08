package tech.sergiodelgado.saastemplate.integration.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
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

    @MockkBean("mockTenantResolver")
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
    fun `billing renders plan details for active subscription`() {
        every { billingService.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = devOrgId,
            externalCustomerId = "cus_test",
            plan = "STARTER",
            status = SubscriptionStatus.ACTIVE,
        )

        mvc.perform(get("/billing"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("STARTER")))
            .andExpect(content().string(containsString("ACTIVE")))
            .andExpect(content().string(containsString("Manage Subscription")))
            .andExpect(content().string(containsString("Upgrade to Pro")))
    }

    @Test
    fun `billing renders trialing subscription and renewal date when currentPeriodEnd is set`() {
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
            .andExpect(content().string(containsString("STARTER")))
            .andExpect(content().string(containsString("TRIALING")))
            // "Renews" text confirms th:if rendered and #temporals.format(Instant) didn't throw.
            .andExpect(content().string(containsString("Renews")))
    }
}
