package tech.sergiodelgado.saastemplate.e2e

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class OnboardingOrganizationE2eTest : PlaywrightE2eTestBase() {

    @Test
    fun `submit button is disabled on load when name field is empty`() {
        page.navigate(url("/onboarding/organization"))

        expectThat(page.locator("#org-submit").isDisabled).isTrue()
    }

    @Test
    fun `submit button enables when a non-empty name is entered`() {
        page.navigate(url("/onboarding/organization"))
        page.locator("input[name='name']").fill("Acme Inc.")

        expectThat(page.locator("#org-submit").isEnabled).isTrue()
    }

    @Test
    fun `submit button re-disables when the name field is cleared`() {
        page.navigate(url("/onboarding/organization"))
        page.locator("input[name='name']").fill("Acme Inc.")
        page.locator("input[name='name']").fill("")

        expectThat(page.locator("#org-submit").isDisabled).isTrue()
    }
}
