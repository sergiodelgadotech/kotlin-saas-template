package tech.sergiodelgado.saastemplate.integration.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import tech.sergiodelgado.saastemplate.organization.OnboardingService
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
        "saasstarter.billing.plan-prices.STARTER=price_starter_test",
        "saasstarter.billing.plan-prices.PRO=price_pro_test",
    ],
)
class OnboardingControllerRenderTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        val devOrgId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean
    lateinit var tenantResolver: TenantResolver

    @MockkBean(relaxed = true)
    lateinit var billingService: BillingService

    @MockkBean(relaxed = true)
    lateinit var onboardingService: OnboardingService

    @BeforeEach
    fun setup() {
        every { tenantResolver.resolveTenantId(any()) } returns devOrgId
    }

    @Test
    fun `GET onboarding organization renders form`() {
        mvc.perform(get("/onboarding/organization"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Create your organization")))
    }

    @Test
    fun `GET onboarding plan renders free-trial card`() {
        mvc.perform(get("/onboarding/plan"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Continue on free trial")))
    }

    @Test
    fun `GET onboarding plan renders paid plan card for configured plans`() {
        mvc.perform(get("/onboarding/plan"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Start with PRO")))
    }

    @Test
    fun `POST onboarding plan with STARTER redirects to dashboard`() {
        mvc.perform(
            post("/onboarding/plan")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("plan", "STARTER"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/dashboard"))
    }
}
