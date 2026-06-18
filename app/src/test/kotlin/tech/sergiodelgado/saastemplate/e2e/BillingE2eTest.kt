package tech.sergiodelgado.saastemplate.e2e

import com.stripe.exception.InvalidRequestException
import io.mockk.every
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.endsWith
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
        every { StripeStubConfig.starterSubscription.createCheckoutSession(any()) } returns StripeStubConfig.CHECKOUT_PATH
        every { StripeStubConfig.starterSubscription.createPortalSession() } returns StripeStubConfig.PORTAL_PATH
    }

    @Test
    fun `billing with no subscription shows no-subscription alert`() {
        every { StripeStubConfig.starterSubscription.currentSubscription() } returns null

        page.navigate(url("/billing"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("No active subscription")
    }

    @Test
    fun `billing with starter subscription shows plan and upgrade button`() {
        page.navigate(url("/billing"))

        val body = page.locator("body").innerText()
        expectThat(body).contains("Starter")
        expectThat(body).contains("Upgrade to Pro")
    }

    @Test
    fun `upgrade to pro redirects to checkout stub`() {
        page.navigate(url("/billing"))
        page.locator("button:has-text('Upgrade to Pro')").click()

        expectThat(page.url()).contains(StripeStubConfig.CHECKOUT_PATH)
    }

    @Test
    fun `manage subscription redirects to portal stub`() {
        every { StripeStubConfig.starterSubscription.currentSubscription() } returns Subscription(
            id = UUID.randomUUID(),
            organizationId = StripeStubConfig.DEV_ORG_ID,
            externalCustomerId = "cus_stub",
            plan = "PRO",
            status = SubscriptionStatus.ACTIVE,
        )

        page.navigate(url("/billing"))
        page.locator("button:has-text('Manage Subscription')").click()

        expectThat(page.url()).contains(StripeStubConfig.PORTAL_PATH)
    }

    @Test
    fun `upgrade to pro when Stripe fails shows error alert on billing page`() {
        every { StripeStubConfig.starterSubscription.createCheckoutSession(any()) } throws
            InvalidRequestException(
                "No such price: 'price_REPLACE_ME'",
                null,
                null,
                "resource_missing",
                400,
                null,
            )

        page.navigate(url("/billing"))
        page.waitForNavigation {
            page.locator("button:has-text('Upgrade to Pro')").click()
        }

        expectThat(page.url()).endsWith("/billing/checkout")
        expectThat(page.locator("body").innerText()).contains("Couldn't start checkout")
    }
}
