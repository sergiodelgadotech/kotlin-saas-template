package org.granchi.saastemplate.billing

import com.stripe.exception.SignatureVerificationException
import com.stripe.net.Webhook
import org.springframework.beans.factory.annotation.Value
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

@RestController
@RequestMapping("/webhooks/stripe")
class StripeWebhookController(private val webhookHandler: StripeWebhookHandler) {

    @Value("\${stripe.webhook-secret}")
    private lateinit var webhookSecret: String

    @PostMapping
    fun handle(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String
    ): ResponseEntity<Unit> {
        val event = try {
            Webhook.constructEvent(payload, signature, webhookSecret)
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

    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("subscription", billingService.currentSubscription())
        return "billing/index"
    }

    @PostMapping("/checkout")
    fun checkout(@RequestParam plan: Subscription.Plan): String {
        val url = billingService.createCheckoutSession(plan)
        return "redirect:$url"
    }

    @PostMapping("/portal")
    fun portal(): String {
        val url = billingService.createPortalSession()
        return "redirect:$url"
    }
}
