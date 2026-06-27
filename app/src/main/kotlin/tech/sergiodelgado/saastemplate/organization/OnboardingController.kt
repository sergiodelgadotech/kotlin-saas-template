package tech.sergiodelgado.saastemplate.organization

import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/organization")
    fun organizationForm(
        @AuthenticationPrincipal oidcUser: OidcUser?,
        model: Model,
    ): String {
        val suggestions = oidcUser?.getClaim<List<*>>("org_suggestions")
            ?.filterIsInstance<String>()
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
        model.addAttribute("suggestions", suggestions)
        return "onboarding/organization"
    }

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
    fun choosePlan(@RequestParam plan: String, model: Model): String {
        return try {
            onboardingService.ensureBilling(plan)
            if (plan == DefaultBillingPlan.STARTER.name) {
                "redirect:/dashboard"
            } else {
                val url = billingService.createCheckoutSession(DefaultBillingPlan.valueOf(plan))
                "redirect:$url"
            }
        } catch (e: StripeException) {
            log.error("Checkout failed for plan {}", plan, e)
            model.addAttribute("plans", properties.billing.planPrices.keys.toList())
            model.addAttribute("starterPlan", DefaultBillingPlan.STARTER.name)
            model.addAttribute("error", "Couldn't start checkout. Try again or continue on the free Starter plan.")
            "onboarding/plan"
        }
    }
}
