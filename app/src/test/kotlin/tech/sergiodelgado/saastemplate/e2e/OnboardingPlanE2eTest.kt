package tech.sergiodelgado.saastemplate.e2e

import com.stripe.exception.InvalidRequestException
import io.mockk.every
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.endsWith

/**
 * E2E tests for the onboarding plan-picker step.
 *
 * RED → GREEN companion for starter #76:
 * "choosing PRO plan during the onboarding fails".
 */
class OnboardingPlanE2eTest : PlaywrightE2eTestBase() {

    @BeforeEach
    fun stubCheckoutToFail() {
        // Reproduce the production failure: Stripe rejects the price ID
        // (e.g. the placeholder price_REPLACE_ME is used).
        // InvalidRequestException(message, param, requestId, code, statusCode, cause)
        every { StripeStubConfig.starterSubscription.createCheckoutSession(any()) } throws
            InvalidRequestException(
                "No such price: 'price_REPLACE_ME'",
                null,
                null,
                "resource_missing",
                400,
                null,
            )
    }

    @AfterEach
    fun resetCheckoutStub() {
        // Restore the default stub so other e2e test classes are unaffected.
        every { StripeStubConfig.starterSubscription.createCheckoutSession(any()) } returns
            StripeStubConfig.CHECKOUT_PATH
    }

    @Test
    fun `choosing PRO plan when Stripe rejects the price shows an error alert on the plan page`() {
        page.navigate(url("/onboarding/plan"))

        // The PRO card has a hidden input name=plan value=PRO and a primary submit button.
        page.waitForNavigation {
            page.locator("form:has(input[name='plan'][value='PRO']) button[type='submit']").click()
        }

        // Desired behaviour: user stays on the plan picker (no raw 500).
        expectThat(page.url()).endsWith("/onboarding/plan")
        // An inline alert should tell the user what happened and offer a way out.
        expectThat(page.locator("body").innerText()).contains("Couldn't start checkout")
    }
}
