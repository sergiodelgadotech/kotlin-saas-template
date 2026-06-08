package tech.sergiodelgado.saastemplate.e2e

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isLessThan
import strikt.assertions.isTrue

class DashboardE2eTest : PlaywrightE2eTestBase() {

    @Test
    fun `dashboard renders nav, sidebar, org name`() {
        page.navigate(url("/dashboard"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("Kotlin SaaS Template")
        expectThat(body).contains("Dashboard")
        expectThat(body).contains("Billing")
        expectThat(body).contains("Dev Org")
    }

    @Test
    fun `dashboard has HTMX stats container`() {
        page.navigate(url("/dashboard"))

        val statsContainer = page.locator("[hx-get='/dashboard/stats']")
        expectThat(statsContainer.count()).isLessThan(2)
        expectThat(statsContainer.isVisible).isTrue()
    }

    @Test
    fun `stats fragment loads via HTMX on page load`() {
        page.navigate(url("/dashboard"))

        // Wait for the HTMX load trigger to fire and swap in the stats fragment.
        page.waitForSelector(".stat-value", com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5_000.0))

        val body = page.locator("body").innerText()
        expectThat(body).contains("Dev Org")
    }

    @Test
    fun `dashboard has SSE activity stream element`() {
        page.navigate(url("/dashboard"))

        val feed = page.locator("#activity-feed")
        expectThat(feed.isVisible).isTrue()
    }
}
