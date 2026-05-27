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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isLessThan
import strikt.assertions.isTrue

@Tag("e2e")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmokeTest {

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
    }

    @AfterEach
    fun tearDown() {
        context.close()
    }

    @Test
    fun `app responds without server error`() {
        // The landing page is served by Cloudflare Pages, not this backend.
        // Verify the backend is up and responding (any non-5xx status is fine).
        val response = page.navigate("http://localhost:$port/")
        expectThat(response!!.status()).isLessThan(500)
    }

    @Test
    fun `health endpoint returns ok`() {
        val response = page.navigate("http://localhost:$port/actuator/health")
        expectThat(response!!.ok()).isTrue()
    }
}
