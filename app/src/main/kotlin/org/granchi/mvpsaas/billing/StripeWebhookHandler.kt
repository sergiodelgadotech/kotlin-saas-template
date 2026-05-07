package org.granchi.mvpsaas.billing

import com.stripe.model.Event
import com.stripe.model.Invoice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class StripeWebhookHandler(
    private val subscriptionRepository: SubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(event: Event) {
        log.info("Processing Stripe event: ${event.type}")
        when (event.type) {
            "customer.subscription.created",
            "customer.subscription.updated"  -> handleSubscriptionUpdate(event)
            "customer.subscription.deleted"  -> handleSubscriptionCanceled(event)
            "invoice.payment_failed"         -> handlePaymentFailed(event)
            else -> log.debug("Ignoring Stripe event: ${event.type}")
        }
    }

    private fun handleSubscriptionUpdate(event: Event) {
        val stripeSub = event.dataObjectDeserializer
            .deserializeUnsafe() as com.stripe.model.Subscription

        val sub = subscriptionRepository.findByStripeCustomerId(stripeSub.customer) ?: run {
            log.warn("No subscription found for Stripe customer ${stripeSub.customer}")
            return
        }

        subscriptionRepository.save(sub.copy(
            stripeSubscriptionId = stripeSub.id,
            plan     = mapPlan(stripeSub),
            status   = mapStatus(stripeSub.status),
            currentPeriodEnd  = java.time.Instant.ofEpochSecond(stripeSub.currentPeriodEnd),
            cancelAtPeriodEnd = stripeSub.cancelAtPeriodEnd
        ))
    }

    private fun handleSubscriptionCanceled(event: Event) {
        val stripeSub = event.dataObjectDeserializer
            .deserializeUnsafe() as com.stripe.model.Subscription

        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSub.id) ?: return
        subscriptionRepository.save(sub.copy(status = Subscription.Status.CANCELED))
    }

    private fun handlePaymentFailed(event: Event) {
        val invoice = event.dataObjectDeserializer.deserializeUnsafe() as Invoice
        val sub = subscriptionRepository.findByStripeCustomerId(invoice.customer) ?: return
        subscriptionRepository.save(sub.copy(status = Subscription.Status.PAST_DUE))
        // TODO: enviar email de aviso via Resend
    }

    private fun mapStatus(status: String) = when (status) {
        "active"   -> Subscription.Status.ACTIVE
        "trialing" -> Subscription.Status.TRIALING
        "past_due" -> Subscription.Status.PAST_DUE
        else       -> Subscription.Status.CANCELED
    }

    private fun mapPlan(stripeSub: com.stripe.model.Subscription): Subscription.Plan {
        val priceId = stripeSub.items.data.firstOrNull()?.price?.id ?: return Subscription.Plan.STARTER
        // TODO: mapear price IDs reales a planes
        return when {
            priceId.contains("pro")        -> Subscription.Plan.PRO
            priceId.contains("enterprise") -> Subscription.Plan.ENTERPRISE
            else                           -> Subscription.Plan.STARTER
        }
    }
}
