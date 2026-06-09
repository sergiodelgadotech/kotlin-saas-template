package tech.sergiodelgado.saastemplate.e2e

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.endsWith
import strikt.assertions.isEqualTo

class OrganizationE2eTest : PlaywrightE2eTestBase() {

    @Test
    fun `settings page renders org name and slug`() {
        page.navigate(url("/organization/settings"))

        // Input values are HTML attributes, not text nodes — use inputValue() not innerText().
        expectThat(page.locator("input[name='name']").inputValue()).isEqualTo("Dev Org")
        expectThat(page.locator("input[disabled]").inputValue()).isEqualTo("dev-org")
    }

    @Test
    fun `settings form submission shows flash message`() {
        page.navigate(url("/organization/settings"))
        page.locator("input[name='name']").fill("Dev Org")
        page.waitForNavigation { page.locator("button[type='submit']").click() }

        val body = page.locator("body").innerText()
        expectThat(body).contains("Settings updated successfully")
    }

    @Test
    fun `members page lists seeded member`() {
        page.navigate(url("/organization/members"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("local-dev-user")
        expectThat(body).contains("OWNER")
    }

    @Test
    fun `sign out redirects to dashboard`() {
        page.navigate(url("/dashboard"))
        page.locator("[role='button'].avatar").click()
        page.locator("form[action='/sign-out'] button[type='submit']").click()

        expectThat(page.url()).endsWith("/dashboard")
    }
}
