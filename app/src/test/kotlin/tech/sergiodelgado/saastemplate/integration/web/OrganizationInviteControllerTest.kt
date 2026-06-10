package tech.sergiodelgado.saastemplate.integration.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class, OrganizationInviteControllerTest.StubConfig::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_invite_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
    ],
)
class OrganizationInviteControllerTest {

    @TestConfiguration
    class StubConfig {
        @Bean
        fun idpUserDirectory(): IdpUserDirectory = IdpUserDirectory { email -> "stub-sub-$email" }
    }

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        val devOrgId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @MockkBean
    lateinit var tenantResolver: TenantResolver

    @BeforeEach
    fun setup() {
        every { tenantResolver.resolveTenantId(any()) } returns devOrgId
    }

    @Test
    fun `GET invite form renders email and role fields`() {
        mvc.perform(get("/organization/members/invite"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("email")))
            .andExpect(content().string(containsString("role")))
    }

    @Test
    fun `POST invite with valid data as OWNER redirects to members with success flash`() {
        mvc.perform(
            post("/organization/members/invite")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@example.com")
                .param("role", "MEMBER"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/organization/members"))
            .andExpect(flash().attribute("success", "Invitation sent to alice@example.com"))

        val member = memberRepository.findByExternalUserId("stub-sub-alice@example.com")
        assertThat(member).isNotNull()
        assertThat(member!!.organizationId).isEqualTo(devOrgId)
        assertThat(member.role).isEqualTo("MEMBER")
    }

    @Test
    fun `POST invite with invalid email re-renders form with error`() {
        mvc.perform(
            post("/organization/members/invite")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "not-an-email")
                .param("role", "MEMBER"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("not-an-email")))
    }

    @Test
    fun `POST invite as MEMBER redirects back with not authorized flash`() {
        mvc.perform(
            post("/organization/members/invite")
                .with(csrf())
                .with(user("member-user"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "bob@example.com")
                .param("role", "MEMBER"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/organization/members"))
            .andExpect(flash().attribute("error", "Not authorized to invite members"))
    }
}
