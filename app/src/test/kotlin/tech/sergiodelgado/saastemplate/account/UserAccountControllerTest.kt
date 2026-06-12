package tech.sergiodelgado.saastemplate.account

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.hamcrest.Matchers.containsString
import tech.sergiodelgado.saastemplate.account.AccountProfile

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class, UserAccountControllerTest.StubConfig::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_account_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
    ],
)
class UserAccountControllerTest {

    @TestConfiguration
    class StubConfig {
        @Bean
        fun idpUserDirectory(): IdpUserDirectory = object : IdpUserDirectory {
            override fun findOrInvite(email: String) = "stub-sub-$email"
            override fun updateProfile(userId: String, givenName: String, familyName: String) = Unit
        }
    }

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        const val USER_SUB = "account-test-sub"
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean
    lateinit var memberRepository: MemberRepository

    @MockkBean
    lateinit var tenantResolver: TenantResolver

    @MockkBean
    lateinit var userAccountService: UserAccountService

    @BeforeEach
    fun stubNav() {
        // NavModelAdvice reads MemberRepository for initials; return null for test users
        every { memberRepository.findByExternalUserId(any()) } returns null
    }

    @Test
    fun `GET account renders first and last name from service`() {
        every { userAccountService.getProfile(USER_SUB) } returns AccountProfile("Alice", "Smith")

        mvc.perform(
            get("/account")
                .with(oidcLogin().idToken { it.subject(USER_SUB).claim("email", "test@example.com") })
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Alice")))
            .andExpect(content().string(containsString("Smith")))
    }

    @Test
    fun `POST account calls service and redirects with flash`() {
        justRun {
            userAccountService.updateDisplayName(any(), any(), any(), any())
        }

        mvc.perform(
            post("/account")
                .with(csrf())
                .with(oidcLogin().idToken { it.subject(USER_SUB).claim("email", "test@example.com") })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("firstName", "Bob")
                .param("lastName", "Jones")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/account"))
            .andExpect(flash().attribute("success", "Profile updated"))

        verify(exactly = 1) {
            userAccountService.updateDisplayName(USER_SUB, "Bob", "Jones", "test@example.com")
        }
    }
}
