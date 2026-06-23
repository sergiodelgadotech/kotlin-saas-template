package tech.sergiodelgado.saastemplate.billing

import com.stripe.exception.SignatureVerificationException
import com.stripe.exception.StripeException
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tech.sergiodelgado.saasstarter.autoconfigure.SaasStarterProperties
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.DefaultBillingPlan
import tech.sergiodelgado.saasstarter.billing.StripeWebhookHandler

@RestController
@RequestMapping("/webhooks/stripe")
class StripeWebhookController(
    private val webhookHandler: StripeWebhookHandler,
    private val properties: SaasStarterProperties,
) {

    @PostMapping
    fun handle(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
    ): ResponseEntity<Unit> {
        val event = try {
            Webhook.constructEvent(payload, signature, properties.billing.webhookSecret)
        } catch (e: SignatureVerificationException) {
            return ResponseEntity.badRequest().build()
        }
        webhookHandler.handle(event)
        return ResponseEntity.ok().build()
    }
}

@Controller
@RequestMapping("/billing")
class BillingController(private val billingService: BillingService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun index(@RequestParam(required = false) success: Boolean?, model: Model): String {
        val subscription = if (success == true) {
            try {
                billingService.syncFromStripe()
            } catch (e: StripeException) {
                log.warn("Stripe sync on return failed; showing DB state", e)
                billingService.currentSubscription()
            }
        } else {
            billingService.currentSubscription()
        }
        model.addAttribute("subscription", subscription)
        return "billing/index"
    }

    @PostMapping("/checkout")
    fun checkout(@RequestParam plan: DefaultBillingPlan, model: Model): String {
        return try {
            billingService.ensureStripeCustomer()
            "redirect:${billingService.createCheckoutSession(plan)}"
        } catch (e: StripeException) {
            log.error("Checkout failed for plan {}", plan, e)
            model.addAttribute("subscription", billingService.currentSubscription())
            model.addAttribute("error", "Couldn't start checkout. Please try again or contact support.")
            "billing/index"
        }
    }

    @PostMapping("/portal")
    fun portal(model: Model): String {
        return try {
            "redirect:${billingService.createPortalSession()}"
        } catch (e: StripeException) {
            log.error("Portal session failed", e)
            model.addAttribute("subscription", billingService.currentSubscription())
            model.addAttribute("error", "Couldn't open the billing portal. Please try again or contact support.")
            "billing/index"
        }
    }
}
