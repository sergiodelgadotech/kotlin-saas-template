package tech.sergiodelgado.saastemplate.integration

import tech.sergiodelgado.saasstarter.autoconfigure.BillingAutoConfiguration
import tech.sergiodelgado.saasstarter.autoconfigure.OrganizationAutoConfiguration
import tech.sergiodelgado.saasstarter.autoconfigure.SessionAutoConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tech.sergiodelgado.saasstarter.autoconfigure.JobRunrAutoConfiguration as StarterJobRunrAutoConfiguration
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.session.SessionRepository
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

    // @Profile prevents this from being picked up by SaasTemplateApplication's
    // component scan during full-context integration tests (which use the "test" profile)
    @Profile("!test")
    @SpringBootApplication(
        exclude = [
            DataSourceAutoConfiguration::class,
            FlywayAutoConfiguration::class,
            DataRedisRepositoriesAutoConfiguration::class,
            JobRunrAutoConfiguration::class,
            StarterJobRunrAutoConfiguration::class,
            OrganizationAutoConfiguration::class,
            BillingAutoConfiguration::class,
            SecurityAutoConfiguration::class,
            OAuth2ClientAutoConfiguration::class,
            ReactiveOAuth2ClientAutoConfiguration::class,
            UserDetailsServiceAutoConfiguration::class,
        ],
    )
    class SmokeTestApp

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
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
