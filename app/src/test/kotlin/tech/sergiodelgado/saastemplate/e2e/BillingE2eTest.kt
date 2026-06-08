package tech.sergiodelgado.saastemplate.e2e

import io.mockk.every
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import java.util.UUID

class BillingE2eTest : PlaywrightE2eTestBase() {

    @BeforeEach
    fun resetBillingMock() {
        every { StripeStubConfig.starterSubscription.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = StripeStubConfig.DEV_ORG_ID,
            externalCustomerId = "cus_stub",
            plan = "STARTER",
            status = SubscriptionStatus.ACTIVE,
        )
    }

    @Test
    fun `billing with no subscription shows no-subscription alert`() {
        every { StripeStubConfig.starterSubscription.currentSubscription() } returns null

        page.navigate(url("/billing"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("No active subscription")
    }

    @Test
    fun `billing with active subscription shows plan and manage button`() {
        page.navigate(url("/billing"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("STARTER")
        expectThat(body).contains("ACTIVE")
        expectThat(body).contains("Manage Subscription")
    }

    @Test
    fun `upgrade to pro redirects to checkout stub`() {
        page.navigate(url("/billing"))
        page.locator("button:has-text('Upgrade to Pro')").click()

        expectThat(page.url()).contains(StripeStubConfig.CHECKOUT_PATH)
    }

    @Test
    fun `manage subscription redirects to portal stub`() {
        page.navigate(url("/billing"))
        page.locator("button:has-text('Manage Subscription')").click()

        expectThat(page.url()).contains(StripeStubConfig.PORTAL_PATH)
    }
}
