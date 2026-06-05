package tech.sergiodelgado.saastemplate.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tech.sergiodelgado.saastemplate.SaasTemplateApplication

@Tag("e2e")
@Testcontainers
@SpringBootTest(
    classes = [SaasTemplateApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test", "local")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
abstract class PlaywrightE2eTestBase {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        private lateinit var playwright: Playwright
        private lateinit var browser: Browser

        @BeforeAll
        @JvmStatic
        fun launchBrowser() {
            playwright = Playwright.create()
            browser = playwright.chromium().launch()
        }

        @AfterAll
        @JvmStatic
        fun closeBrowser() {
            browser.close()
            playwright.close()
        }
    }

    @LocalServerPort
    protected var port: Int = 0

    @RegisterExtension
    val tracing = PlaywrightFailureArtifacts()

    protected lateinit var context: BrowserContext
    protected lateinit var page: Page

    @BeforeEach
    fun setupBrowserContext() {
        context = browser.newContext()
        page = context.newPage()
        tracing.bind(context, page)
    }

    @AfterEach
    fun tearDownBrowserContext() {
        context.close()
    }

    protected fun url(path: String) = "http://localhost:$port$path"
}
