package tech.sergiodelgado.saastemplate.organization

import com.stripe.exception.InvalidRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.ui.ExtendedModelMap
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsKey
import strikt.assertions.isEqualTo
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterProperties
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.DefaultBillingPlan
import tech.sergiodelgado.saasstarter.organization.Organization

class OnboardingControllerTest {

    private val onboardingService = mockk<OnboardingService>(relaxed = true)
    private val billingService = mockk<BillingService>(relaxed = true)
    private val properties = SaasStarterProperties(
        billing = SaasStarterProperties.Billing(
            planPrices = mapOf("STARTER" to "price_starter", "PRO" to "price_pro"),
        )
    )

    private val controller = OnboardingController(onboardingService, billingService, properties)

    private fun oidcUser(subject: String = "user-sub", email: String = "u@example.com") =
        mockk<OidcUser> {
            every { this@mockk.subject } returns subject
            every { this@mockk.email } returns email
            every { givenName } returns "Alice"
            every { familyName } returns "Smith"
        }

    @Test
    fun `GET organization returns onboarding-organization template`() {
        expectThat(controller.organizationForm()).isEqualTo("onboarding/organization")
    }

    @Test
    fun `POST organization calls createOrganization with OIDC principal fields`() {
        every { onboardingService.createOrganization(any(), any(), any(), any(), any()) } returns
            Organization(name = "Acme", slug = "acme-abc123")

        controller.createOrganization("Acme", oidcUser())

        verify(exactly = 1) {
            onboardingService.createOrganization(
                ownerUserId = "user-sub",
                name = "Acme",
                email = "u@example.com",
                firstName = "Alice",
                lastName = "Smith",
            )
        }
    }

    @Test
    fun `POST organization redirects to onboarding plan`() {
        every { onboardingService.createOrganization(any(), any(), any(), any(), any()) } returns
            Organization(name = "Acme", slug = "acme-abc123")

        val result = controller.createOrganization("Acme", oidcUser())

        expectThat(result).isEqualTo("redirect:/onboarding/plan")
    }

    @Test
    fun `GET plan renders plan template with plan list from properties`() {
        val model = ExtendedModelMap()

        val view = controller.planForm(model)

        expectThat(view).isEqualTo("onboarding/plan")
        expectThat(model).containsKey("plans")
        expectThat(model).containsKey("starterPlan")
    }

    @Test
    fun `POST plan with STARTER redirects to dashboard`() {
        val result = controller.choosePlan(DefaultBillingPlan.STARTER.name, ExtendedModelMap())

        expectThat(result).isEqualTo("redirect:/dashboard")
    }

    @Test
    fun `POST plan with paid plan invokes checkout session and redirects`() {
        every { billingService.createCheckoutSession(DefaultBillingPlan.PRO) } returns "https://checkout.stripe.com/pay/cs_test"

        val result = controller.choosePlan("PRO", ExtendedModelMap())

        expectThat(result).isEqualTo("redirect:https://checkout.stripe.com/pay/cs_test")
    }

    @Test
    fun `POST plan with Stripe exception re-renders plan form with error message`() {
        every { billingService.createCheckoutSession(DefaultBillingPlan.PRO) } throws
            InvalidRequestException("No such price: 'price_REPLACE_ME'", null, null, "resource_missing", 400, null)
        val model = ExtendedModelMap()

        val view = controller.choosePlan("PRO", model)

        expectThat(view).isEqualTo("onboarding/plan")
        expectThat(model).containsKey("plans")
        expectThat(model).containsKey("starterPlan")
        expectThat(model["error"] as String).contains("Couldn't start checkout")
    }
}
