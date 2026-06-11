package tech.sergiodelgado.saastemplate.integration

import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class FlywayMigrationTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    // ── Starter migrations ────────────────────────────────────────────────────

    @Test
    fun `V100 starter baseline creates organizations members and subscriptions tables`() {
        val tables = publicTables()
        expectThat(tables).contains("organizations")
        expectThat(tables).contains("members")
        expectThat(tables).contains("subscriptions")
    }

    // ── App migrations ────────────────────────────────────────────────────────

    @Test
    fun `V200 app init creates imports and analysis_results tables`() {
        val tables = publicTables()
        expectThat(tables).contains("imports")
        expectThat(tables).contains("analysis_results")
    }

    // ── Flyway history ────────────────────────────────────────────────────────

    @Test
    fun `all expected migrations are recorded as successful in flyway_schema_history`() {
        val applied = jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
            String::class.java
        )
        expectThat(applied).contains("100")
        expectThat(applied).contains("200")
    }

    private fun publicTables(): List<String?> =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String::class.java
        )
}
