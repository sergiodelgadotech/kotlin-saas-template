package tech.sergiodelgado.saastemplate.integration

import tech.sergiodelgado.saasstarter.autoconfigure.BillingAutoConfiguration
import tech.sergiodelgado.saasstarter.autoconfigure.OrganizationAutoConfiguration
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration as RedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Profile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import strikt.api.expectThat
import strikt.assertions.contains

@Tag("integration")
@SpringBootTest(
    classes = [ObservabilityAutoConfigSmokeTest.SmokeTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@AutoConfigureMockMvc(addFilters = false)
class ObservabilityAutoConfigSmokeTest {

    @Profile("!test")
    @SpringBootApplication(
        exclude = [
            DataSourceAutoConfiguration::class,
            FlywayAutoConfiguration::class,
            RedisAutoConfiguration::class,
            tech.sergiodelgado.saasstarter.autoconfigure.RedisAutoConfiguration::class,
            tech.sergiodelgado.saasstarter.autoconfigure.SessionAutoConfiguration::class,
            DataRedisRepositoriesAutoConfiguration::class,
            JobRunrAutoConfiguration::class,
            tech.sergiodelgado.saasstarter.autoconfigure.JobRunrAutoConfiguration::class,
            OrganizationAutoConfiguration::class,
            BillingAutoConfiguration::class,
        ],
    )
    class SmokeTestApp

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `prometheus endpoint is reachable and returns 200`() {
        mockMvc.get("/actuator/prometheus").andExpect { status { isOk() } }
    }

    @Test
    fun `prometheus endpoint exposes standard JVM metrics`() {
        val result = mockMvc.get("/actuator/prometheus")
            .andExpect { status { isOk() } }
            .andReturn()
        expectThat(result.response.contentAsString).contains("jvm_memory_used_bytes")
    }
}
