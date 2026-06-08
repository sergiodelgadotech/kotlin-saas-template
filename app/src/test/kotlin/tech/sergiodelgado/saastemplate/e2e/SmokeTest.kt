package tech.sergiodelgado.saastemplate.e2e

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isTrue

class SmokeTest : PlaywrightE2eTestBase() {

    @Test
    fun `app responds without server error`() {
        val response = page.navigate(url("/dashboard"))
        expectThat(response!!.ok()).isTrue()
    }

    @Test
    fun `health endpoint returns ok`() {
        val response = page.navigate(url("/actuator/health"))
        expectThat(response!!.ok()).isTrue()
    }
}
