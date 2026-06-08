package tech.sergiodelgado.saastemplate.integration

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class, WebMvcAutoConfigSmokeTest.ProbeConfig::class])
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
    // Autowire the ProbeConfig bean itself to access the call counters.
    @Autowired private lateinit var probeConfig: ProbeConfig

    @BeforeEach
    fun reset() {
        probeConfig.isAllowedCallCount.set(0)
        probeConfig.resolveTenantIdCallCount.set(0)
    }

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
        mockMvc.perform(get("/webhooks/__probe"))
        expectThat(probeConfig.isAllowedCallCount.get()).isGreaterThan(0)
    }

    @Test
    fun `tenant interceptor fires on app paths`() {
        mockMvc.perform(get("/app/__probe").requestAttr("auth_user_id", "probe-user"))
        expectThat(probeConfig.resolveTenantIdCallCount.get()).isGreaterThan(0)
    }

    @Test
    fun `tenant interceptor is excluded from webhook paths`() {
        mockMvc.perform(get("/webhooks/__probe"))
        expectThat(probeConfig.resolveTenantIdCallCount.get()).isEqualTo(0)
    }

    // Provides call-counting test doubles for RateLimiter and TenantResolver.
    // Counters live here so the test class can reset and assert them via @Autowired.
    @Configuration(proxyBeanMethods = false)
    @Profile("test")
    class ProbeConfig {
        val isAllowedCallCount = AtomicInteger(0)
        val resolveTenantIdCallCount = AtomicInteger(0)

        @Bean @Primary
        fun probeRateLimiter(
            @Suppress("UNCHECKED_CAST")
            redisTemplate: RedisTemplate<String, Any>,
        ): RateLimiter = object : RateLimiter(redisTemplate) {
            override fun isAllowed(key: String, limit: Int, window: Duration): Boolean {
                isAllowedCallCount.incrementAndGet()
                return true
            }
        }

        @Bean @Primary
        fun probeTenantResolver(): TenantResolver = TenantResolver { _ ->
            resolveTenantIdCallCount.incrementAndGet()
            null
        }
    }
}

// Profile-gated probe controller providing the routes the smoke tests hit.
@org.springframework.web.bind.annotation.RestController
@Profile("test")
class WebMvcAutoConfigProbeController {
    @org.springframework.web.bind.annotation.GetMapping("/app/__probe") fun appProbe() = mapOf("ok" to true)
    @org.springframework.web.bind.annotation.GetMapping("/webhooks/__probe") fun webhookProbe() = mapOf("ok" to true)
}
