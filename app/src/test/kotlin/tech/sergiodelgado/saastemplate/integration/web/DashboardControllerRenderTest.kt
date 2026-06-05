package tech.sergiodelgado.saastemplate.integration.web

import io.mockk.clearMocks
import io.mockk.every
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@AutoConfigureMockMvc
@Import(WebRenderTestMocks::class)
@ActiveProfiles("test", "local")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
class DashboardControllerRenderTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun resetMocks() = clearMocks(WebRenderTestMocks.billing)

    @Test
    fun `dashboard renders org name and subscription plan`() {
        every { WebRenderTestMocks.billing.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            externalCustomerId = "cus_test",
            plan = "STARTER",
            status = SubscriptionStatus.TRIALING,
        )

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dev Org")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("STARTER")))
    }

    @Test
    fun `dashboard renders without subscription`() {
        every { WebRenderTestMocks.billing.currentSubscription() } returns null

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dev Org")))
    }

    @Test
    fun `dashboard has HTMX stats poll attribute`() {
        every { WebRenderTestMocks.billing.currentSubscription() } returns null

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-get=\"/dashboard/stats\"")))
    }

    @Test
    fun `dashboard has SSE activity stream attribute`() {
        every { WebRenderTestMocks.billing.currentSubscription() } returns null

        mvc.perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("sse-connect=\"/dashboard/activity-stream\"")))
    }

    @Test
    fun `stats fragment renders org name and subscription plan`() {
        every { WebRenderTestMocks.billing.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            externalCustomerId = "cus_test",
            plan = "STARTER",
            status = SubscriptionStatus.TRIALING,
        )

        mvc.perform(get("/dashboard/stats"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dev Org")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("STARTER")))
    }

    @Test
    fun `stats fragment renders without subscription`() {
        every { WebRenderTestMocks.billing.currentSubscription() } returns null

        mvc.perform(get("/dashboard/stats"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dev Org")))
    }
}
