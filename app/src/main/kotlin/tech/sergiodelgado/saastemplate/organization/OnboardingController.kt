package tech.sergiodelgado.saastemplate.organization

import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
import tech.sergiodelgado.saastemplate.auth.OrgSuggestions

@Controller
@RequestMapping("/onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService,
    private val billingService: BillingService,
    private val properties: SaasStarterProperties,
    @Autowired(required = false) private val orgSuggestions: OrgSuggestions? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/organization")
    fun organizationForm(
        @AuthenticationPrincipal oidcUser: OidcUser?,
        model: Model,
    ): String {
        val suggestions = oidcUser?.let { user ->
            // Definitive: Google Workspace hd or Microsoft Entra tenant ID
            deriveOrgSuggestions(user.getClaim("hd"), user.getClaim("tid"), user.email)
                // More precise than email domain: actual GitHub public org memberships
                ?: user.subject?.let { sub -> orgSuggestions?.getOrgNames(sub) }
                // Last resort: email domain heuristic for any other provider
                ?: deriveEmailDomainSuggestion(user.email)
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
 * Returns org name candidates from definitive OIDC claims (Google Workspace hd, MS Entra tid).
 * Returns null when neither is present — caller should try other sources before falling back
 * to the email domain heuristic.
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

    return null
}

/**
 * Last-resort heuristic: derives an org name candidate from the email domain.
 * Returns null for consumer domains (gmail.com, hotmail.com, etc.) and null emails.
 */
internal fun deriveEmailDomainSuggestion(email: String?): List<String>? {
    val domain = email?.substringAfter("@") ?: return null
    if (domain in CONSUMER_DOMAINS) return null
    return listOf(domainLabel(domain.substringBefore(".")))
}

private fun domainLabel(domain: String): String =
    domain.substringBefore(".").replaceFirstChar(Char::uppercase).replace("-", " ")
