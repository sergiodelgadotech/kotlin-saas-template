package org.granchi.saastemplate.integration

import org.granchi.saasstarter.autoconfigure.SessionAutoConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.session.SessionRepository
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

// Boots a minimal Spring Boot app (no DB, no Flyway) with a real Redis container
// to verify that SessionAutoConfiguration is discovered via the imports file
// and that it provides a SessionRepository bean.
@Tag("integration")
@Testcontainers
@SpringBootTest(
    classes = [SessionAutoConfigSmokeTest.SmokeTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class SessionAutoConfigSmokeTest {

    @SpringBootApplication(
        exclude = [
            DataSourceAutoConfiguration::class,
            FlywayAutoConfiguration::class,
            RedisRepositoriesAutoConfiguration::class,
            JobRunrAutoConfiguration::class,
        ],
    )
    class SmokeTestApp

    companion object {
        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.url") { "redis://${redis.host}:${redis.getMappedPort(6379)}" }
        }
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `SessionAutoConfiguration is discovered via the imports file`() {
        val configs = context.getBeansOfType(SessionAutoConfiguration::class.java)
        expectThat(configs).hasSize(1)
    }

    @Test
    fun `Spring Session provides a SessionRepository bean`() {
        val repos = context.getBeansOfType(SessionRepository::class.java)
        expectThat(repos.values.firstOrNull()).isNotNull()
    }
}
