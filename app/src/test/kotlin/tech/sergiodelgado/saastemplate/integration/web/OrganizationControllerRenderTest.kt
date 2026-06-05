package tech.sergiodelgado.saastemplate.integration.web

import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

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
class OrganizationControllerRenderTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `settings page renders org name and slug`() {
        mvc.perform(get("/organization/settings"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Dev Org")))
            .andExpect(content().string(containsString("dev-org")))
    }

    @Test
    fun `settings form POST redirects with success flash`() {
        mvc.perform(
            post("/organization/settings")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Dev Org Renamed"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/organization/settings"))
            .andExpect(flash().attribute("success", "Settings updated successfully"))
    }

    @Test
    fun `members page renders member row`() {
        mvc.perform(get("/organization/members"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("local-dev-user")))
            .andExpect(content().string(containsString("OWNER")))
    }
}
