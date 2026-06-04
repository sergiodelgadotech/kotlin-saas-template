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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isLessThan
import strikt.assertions.isTrue

@Tag("e2e")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// "local" activates LocalDevAuthFilter + FilterRegistrationBean guard.
// "test" supplies JwtAuthFilter config stubs and Sentry/JobRunr no-ops.
@ActiveProfiles("test", "local")
// Force TC JDBC so application-local.yml (if present) cannot override the datasource,
// and add the local seed migration so local-dev-user has a tenant in the test DB.
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_test",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
    ],
)
class SmokeTest {

    @RegisterExtension
    val tracing = PlaywrightFailureArtifacts()

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
    private var port: Int = 0

    private lateinit var context: BrowserContext
    private lateinit var page: Page

    @BeforeEach
    fun setup() {
        context = browser.newContext()
        page = context.newPage()
        tracing.bind(context, page)
    }

    @AfterEach
    fun tearDown() {
        context.close()
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Test
    fun `app responds without server error`() {
        val response = page.navigate("http://localhost:$port/")
        expectThat(response!!.status()).isLessThan(500)
    }

    @Test
    fun `health endpoint returns ok`() {
        val response = page.navigate("http://localhost:$port/actuator/health")
        expectThat(response!!.ok()).isTrue()
    }

    // ── Authenticated pages ───────────────────────────────────────────────────
    // LocalDevAuthFilter (active in "local" profile) authenticates every request
    // as local-dev-user. The seed migration provides the matching org + member row.

    @Test
    fun `dashboard page renders without error`() {
        val response = page.navigate("http://localhost:$port/dashboard")
        expectThat(response!!.status()).isLessThan(400)
        expectThat(page.locator("body").innerText()).not().contains("500")
        expectThat(page.locator("body").innerText()).not().contains("Server Error")
    }

    @Test
    fun `billing page renders without error`() {
        val response = page.navigate("http://localhost:$port/billing")
        expectThat(response!!.status()).isLessThan(400)
        expectThat(page.locator("body").innerText()).not().contains("500")
        expectThat(page.locator("body").innerText()).not().contains("Server Error")
    }

    @Test
    fun `organization members page renders without error`() {
        val response = page.navigate("http://localhost:$port/organization/members")
        expectThat(response!!.status()).isLessThan(400)
        expectThat(page.locator("body").innerText()).not().contains("500")
        expectThat(page.locator("body").innerText()).not().contains("Server Error")
    }
}
