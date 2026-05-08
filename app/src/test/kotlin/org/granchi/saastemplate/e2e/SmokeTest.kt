package org.granchi.saastemplate.e2e

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
import strikt.api.expectThat
import strikt.assertions.isTrue

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmokeTest {

    @LocalServerPort
    private var port: Int = 0

    companion object {
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
    fun `landing page loads successfully`() {
        page.navigate("http://localhost:$port/")
        expectThat(page.title().isNotEmpty()).isTrue()
    }

    @Test
    fun `health endpoint returns ok`() {
        val response = page.navigate("http://localhost:$port/actuator/health")
        expectThat(response!!.ok()).isTrue()
    }
}
