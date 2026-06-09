package tech.sergiodelgado.saastemplate.integration

import tech.sergiodelgado.saasstarter.autoconfigure.BillingAutoConfiguration
import tech.sergiodelgado.saasstarter.autoconfigure.OrganizationAutoConfiguration
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterAutoConfiguration
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterProperties
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration as RedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isTrue

// Boots a minimal Spring Boot app rather than SaasTemplateApplication so the
// test verifies autoconfig discovery (the AutoConfiguration.imports file is
// honoured) without dragging in template wiring that is unrelated to Plan 1.
// Heavy autoconfigs that need real infrastructure are excluded — discovery via
// the imports file still happens, which is what this test exists to prove.
@Tag("integration")
@SpringBootTest(
    classes = [StarterAutoConfigSmokeTest.SmokeTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class StarterAutoConfigSmokeTest {

    // @Profile prevents this from being picked up by SaasTemplateApplication's
    // component scan during full-context integration tests (which use the "test" profile)
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
            SecurityAutoConfiguration::class,
            OAuth2ClientAutoConfiguration::class,
            ReactiveOAuth2ClientAutoConfiguration::class,
            UserDetailsServiceAutoConfiguration::class,
        ],
    )
    class SmokeTestApp

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var properties: SaasStarterProperties

    @Test
    fun `SaasStarterAutoConfiguration is discovered via the imports file`() {
        val beans = context.getBeansOfType(SaasStarterAutoConfiguration::class.java)
        expectThat(beans).hasSize(1)
    }

    @Test
    fun `SaasStarterProperties default enabled flag is true`() {
        expectThat(properties.enabled).isTrue()
    }
}
