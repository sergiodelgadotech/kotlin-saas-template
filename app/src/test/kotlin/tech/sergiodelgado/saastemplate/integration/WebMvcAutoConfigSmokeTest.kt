package tech.sergiodelgado.saastemplate.integration

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterProperties
import tech.sergiodelgado.saasstarter.autoconfigure.WebMvcAutoConfiguration
import tech.sergiodelgado.saasstarter.ratelimit.RateLimitInterceptor
import tech.sergiodelgado.saasstarter.ratelimit.RateLimiter
import tech.sergiodelgado.saasstarter.tenant.TenantInterceptor
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import java.time.Duration
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class WebMvcAutoConfigSmokeTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var tenantResolver: TenantResolver
    @Autowired private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun resetMocks() = clearAllMocks()

    @Test
    fun `WebMvcAutoConfiguration is discovered and beans are wired`() {
        expectThat(context.getBeansOfType(WebMvcAutoConfiguration::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(TenantInterceptor::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(RateLimitInterceptor::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(RateLimiter::class.java)).hasSize(1)
    }

    @Test
    fun `tenant and rate-limit path patterns bind from application yml`() {
        val props = context.getBean(SaasStarterProperties::class.java)
        expectThat(props.tenant.pathPatterns).contains("/app/**", "/dashboard/**", "/billing/**", "/organization/**")
        expectThat(props.tenant.excludePathPatterns).contains("/webhooks/**", "/", "/pricing", "/docs/**")
        expectThat(props.rateLimit.pathPatterns).contains("/webhooks/**")
    }

    @Test
    fun `rate limit interceptor fires on webhooks paths`() {
        every { rateLimiter.isAllowed(any(), any(), any()) } returns true

        mockMvc.perform(get("/webhooks/__probe"))

        verify(exactly = 1) { rateLimiter.isAllowed(any(), limit = 100, window = Duration.ofMinutes(1)) }
    }

    @Test
    fun `tenant interceptor fires on app paths`() {
        val tenantId = UUID.randomUUID()
        every { tenantResolver.resolveTenantId(any()) } returns tenantId

        // "auth_user_id" is the request attribute set by JwtAuthFilter — simulate it directly
        mockMvc.perform(get("/app/__probe").requestAttr("auth_user_id", "probe-user"))

        verify(atLeast = 1) { tenantResolver.resolveTenantId(any()) }
    }

    @Test
    fun `tenant interceptor is excluded from webhook paths`() {
        every { rateLimiter.isAllowed(any(), any(), any()) } returns true

        mockMvc.perform(get("/webhooks/__probe"))

        verify(exactly = 0) { tenantResolver.resolveTenantId(any()) }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("test")
    class ProbeConfig {
        @Bean @Primary fun mockRateLimiter(): RateLimiter = mockk(relaxed = true)
        @Bean @Primary fun mockTenantResolver(): TenantResolver = mockk(relaxed = true)
    }
}

// Profile-gated probe controller providing the routes the smoke tests hit.
@org.springframework.web.bind.annotation.RestController
@Profile("test")
class WebMvcAutoConfigProbeController {
    @org.springframework.web.bind.annotation.GetMapping("/app/__probe") fun appProbe() = mapOf("ok" to true)
    @org.springframework.web.bind.annotation.GetMapping("/webhooks/__probe") fun webhookProbe() = mapOf("ok" to true)
}
