package tech.sergiodelgado.saastemplate.organization

import com.stripe.exception.InvalidRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.ui.ExtendedModelMap
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsKey
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterProperties
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.DefaultBillingPlan
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.util.UUID

class OnboardingControllerTest {

    private val onboardingService = mockk<OnboardingService>(relaxed = true)
    private val billingService = mockk<BillingService>(relaxed = true)
    private val properties = SaasStarterProperties(
        billing = SaasStarterProperties.Billing(
            planPrices = mapOf("STARTER" to "price_starter", "PRO" to "price_pro"),
        )
    )

    private val controller = OnboardingController(onboardingService, billingService, properties)

    private val testOrgId = UUID.randomUUID()

    @BeforeEach
    fun setTenantContext() {
        TenantContext.set(testOrgId)
    }

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }

    private fun oidcUser(
        subject: String = "user-sub",
        email: String = "u@example.com",
        hd: String? = null,
        tid: String? = null,
    ) = mockk<OidcUser> {
        every { this@mockk.subject } returns subject
        every { this@mockk.email } returns email
        every { givenName } returns "Alice"
        every { familyName } returns "Smith"
        every { getClaim<String>("hd") } returns hd
        every { getClaim<String>("tid") } returns tid
    }

    @Test
    fun `GET organization returns onboarding-organization template`() {
        val model = ExtendedModelMap()
        expectThat(controller.organizationForm(oidcUser(), model)).isEqualTo("onboarding/organization")
    }

    @Test
    fun `GET organization passes Google Workspace hd suggestion to model`() {
        val model = ExtendedModelMap()
        controller.organizationForm(oidcUser(hd = "acme.com"), model)
        expectThat(model["suggestions"]).isEqualTo(listOf("Acme"))
    }

    @Test
    fun `GET organization passes null suggestions for personal email when no hd or tid`() {
        val model = ExtendedModelMap()
        controller.organizationForm(oidcUser(email = "user@gmail.com"), model)
        expectThat(model["suggestions"]).isNull()
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
    fun `POST plan calls ensureBilling before routing`() {
        controller.choosePlan(DefaultBillingPlan.STARTER.name, ExtendedModelMap())

        verify(exactly = 1) { onboardingService.ensureBilling() }
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

    // ── deriveOrgSuggestions ─────────────────────────────────────────────────

    @Test
    fun `deriveOrgSuggestions returns hd domain name for Google Workspace`() {
        expectThat(deriveOrgSuggestions(hd = "acme.com", tid = null, email = null))
            .isEqualTo(listOf("Acme"))
    }

    @Test
    fun `deriveOrgSuggestions returns email domain label for Entra ID work account`() {
        expectThat(
            deriveOrgSuggestions(
                hd = null,
                tid = "aaaabbbb-cccc-dddd-eeee-ffffaaaabbbb",
                email = "alice@contoso.com",
            )
        ).isEqualTo(listOf("Contoso"))
    }

    @Test
    fun `deriveOrgSuggestions returns null for Microsoft consumer pseudo-tenant`() {
        expectThat(
            deriveOrgSuggestions(
                hd = null,
                tid = "9188040d-6c67-4c5b-b112-36a304b66dad",
                email = "user@hotmail.com",
            )
        ).isNull()
    }

    @Test
    fun `deriveOrgSuggestions returns email domain label for business GitHub email`() {
        expectThat(deriveOrgSuggestions(hd = null, tid = null, email = "dev@mycompany.io"))
            .isEqualTo(listOf("Mycompany"))
    }

    @Test
    fun `deriveOrgSuggestions returns null for personal Gmail`() {
        expectThat(deriveOrgSuggestions(hd = null, tid = null, email = "user@gmail.com"))
            .isNull()
    }

    @Test
    fun `deriveOrgSuggestions returns null when no claims at all`() {
        expectThat(deriveOrgSuggestions(hd = null, tid = null, email = null))
            .isNull()
    }

    @Test
    fun `deriveOrgSuggestions handles hyphenated domain label`() {
        expectThat(deriveOrgSuggestions(hd = null, tid = null, email = "user@my-company.com"))
            .isEqualTo(listOf("My company"))
    }
}
