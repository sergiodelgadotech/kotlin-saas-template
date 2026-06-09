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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import tech.sergiodelgado.saastemplate.SaasTemplateApplication

@Tag("e2e")
@SpringBootTest(
    classes = [SaasTemplateApplication::class, StripeStubConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///saastemplate_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration/saasstarter,classpath:db/migration/local",
        "spring.main.allow-bean-definition-overriding=true",
        "management.health.redis.enabled=false",
        // Disable the starter's Redis-backed session so flash attributes use Jetty's native in-memory session.
        // SessionRepositoryFilter writes happen outside DispatcherServlet, so a Redis hiccup there produces a
        // 500 that bypasses GlobalExceptionHandler and shows via BasicErrorController.
        "saasstarter.session.enabled=false",
    ],
)
abstract class PlaywrightE2eTestBase {

    companion object {
        // A JVM-level singleton Redis container shared across all e2e test classes.
        //
        // Using @Container+@ServiceConnection would start/stop the container per test class
        // (TC's per-class lifecycle). All subclasses share the same Spring context (same
        // container object → same context cache key), so after the first class stops the
        // container the cached context's Lettuce connection is dead — causing a 60 s Redis
        // command timeout on every subsequent @Cacheable call.
        //
        // Starting the container once here and wiring it via @DynamicPropertySource keeps the
        // connection alive for the entire test run. TC's JVM shutdown hook cleans it up.
        private val redis: GenericContainer<*> by lazy {
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)
                .also { it.start() }
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureRedis(registry: DynamicPropertyRegistry) {
            // application-test.yml sets spring.data.redis.url=redis://localhost:6379 (profile
            // priority). @DynamicPropertySource ranks higher than profile properties, so setting
            // url here overrides it with the TC container's actual host:port.
            // Setting only host/port would be ignored because Spring Data Redis gives url
            // precedence over host+port when both are present.
            registry.add("spring.data.redis.url") { "redis://${redis.host}:${redis.firstMappedPort}" }
        }

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
