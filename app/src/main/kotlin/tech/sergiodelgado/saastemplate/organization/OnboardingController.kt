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
        val suggestions = oidcUser?.let { user ->
            deriveOrgSuggestions(
                hd = user.getClaim("hd"),
                tid = user.getClaim("tid"),
                email = user.email,
            )
        }
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

private const val MICROSOFT_CONSUMER_TENANT = "9188040d-6c67-4c5b-b112-36a304b66dad"

private val CONSUMER_DOMAINS = setOf(
    "gmail.com", "googlemail.com",
    "hotmail.com", "hotmail.es", "hotmail.fr", "hotmail.co.uk",
    "outlook.com", "outlook.es",
    "yahoo.com", "yahoo.es", "yahoo.fr", "yahoo.co.uk",
    "icloud.com", "me.com", "mac.com",
    "live.com", "msn.com",
    "proton.me", "protonmail.com",
    "aol.com",
)

/**
 * Derives a short list of org name candidates from available OIDC claims.
 * Returns null when no useful signal is present (e.g. personal consumer email).
 */
internal fun deriveOrgSuggestions(hd: String?, tid: String?, email: String?): List<String>? {
    // Google Workspace: hd claim is only present for Workspace (not personal Gmail)
    if (!hd.isNullOrBlank()) return listOf(domainLabel(hd))

    // Microsoft Entra ID work/school accounts carry a tid claim; the well-known
    // consumer pseudo-tenant is the same fixed UUID for all personal MS accounts.
    if (tid != null && tid != MICROSOFT_CONSUMER_TENANT) {
        val label = email?.substringAfter("@")?.substringBefore(".") ?: return null
        return listOf(domainLabel(label))
    }

    // Generic: use the email domain when it is not a common consumer provider.
    val domain = email?.substringAfter("@") ?: return null
    if (domain in CONSUMER_DOMAINS) return null
    return listOf(domainLabel(domain.substringBefore(".")))
}

private fun domainLabel(domain: String): String =
    domain.substringBefore(".").replaceFirstChar(Char::uppercase).replace("-", " ")
