package org.granchi.mvpsaas.billing

import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import org.granchi.saasstarter.tenant.TenantContext
import org.granchi.saasstarter.web.NotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BillingService(
    private val subscriptionRepository: SubscriptionRepository
) {
    @Value("\${stripe.price-id.starter}") private lateinit var starterPriceId: String
    @Value("\${stripe.price-id.pro}")     private lateinit var proPriceId: String
    @Value("\${app.base-url}")            private lateinit var baseUrl: String

    fun currentSubscription(): Subscription =
        subscriptionRepository.findByOrganizationId(TenantContext.get())
            ?: throw NotFoundException("No subscription found for organization")

    fun createCheckoutSession(plan: Subscription.Plan): String {
        val sub = currentSubscription()
        val session = Session.create(
            SessionCreateParams.builder()
                .setCustomer(sub.stripeCustomerId)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceIdFor(plan))
                        .setQuantity(1)
                        .build()
                )
                .setSuccessUrl("$baseUrl/billing?success=true")
                .setCancelUrl("$baseUrl/billing")
                .build()
        )
        return session.url
    }

    fun createPortalSession(): String {
        val sub = currentSubscription()
        return com.stripe.model.billingportal.Session.create(
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(sub.stripeCustomerId)
                .setReturnUrl("$baseUrl/billing")
                .build()
        ).url
    }

    private fun priceIdFor(plan: Subscription.Plan) = when (plan) {
        Subscription.Plan.STARTER -> starterPriceId
        Subscription.Plan.PRO     -> proPriceId
        else -> error("Plan $plan cannot be selected from UI")
    }
}
