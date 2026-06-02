package tech.sergiodelgado.saastemplate

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNotNull

class StaticAssetsTest {

    @Test
    fun `css output is present in classpath`() {
        val resource = StaticAssetsTest::class.java.getResourceAsStream("/static/css/output.css")
        expectThat(resource).isNotNull()
        resource?.close()
    }

    @Test
    fun `htmx is present in classpath`() {
        val resource = StaticAssetsTest::class.java.getResourceAsStream("/static/js/htmx.min.js")
        expectThat(resource).isNotNull()
        resource?.close()
    }

    @Test
    fun `htmx sse extension is present in classpath`() {
        val resource = StaticAssetsTest::class.java.getResourceAsStream("/static/js/htmx-sse.js")
        expectThat(resource).isNotNull()
        resource?.close()
    }
}
