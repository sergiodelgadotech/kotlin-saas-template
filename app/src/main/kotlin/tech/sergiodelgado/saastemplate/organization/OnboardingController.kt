package tech.sergiodelgado.saastemplate.organization

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterProperties
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.DefaultBillingPlan

@Controller
@RequestMapping("/onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService,
    private val billingService: BillingService,
    private val properties: SaasStarterProperties,
) {

    @GetMapping("/organization")
    fun organizationForm(): String = "onboarding/organization"

    @PostMapping("/organization")
    fun createOrganization(
        @RequestParam name: String,
        @AuthenticationPrincipal oidcUser: OidcUser,
    ): String {
        val subject = requireNotNull(oidcUser.subject) { "OIDC subject must not be null" }
        onboardingService.createOrganization(
            ownerUserId = subject,
            name = name,
            email = oidcUser.email.orEmpty(),
            firstName = oidcUser.givenName,
            lastName = oidcUser.familyName,
        )
        return "redirect:/onboarding/plan"
    }

    @GetMapping("/plan")
    fun planForm(model: Model): String {
        model.addAttribute("plans", properties.billing.planPrices.keys.toList())
        model.addAttribute("starterPlan", DefaultBillingPlan.STARTER.name)
        return "onboarding/plan"
    }

    @PostMapping("/plan")
    fun choosePlan(@RequestParam plan: String): String {
        if (plan == DefaultBillingPlan.STARTER.name) {
            return "redirect:/dashboard"
        }
        val billingPlan = DefaultBillingPlan.valueOf(plan)
        val url = billingService.createCheckoutSession(billingPlan)
        return "redirect:$url"
    }
}
