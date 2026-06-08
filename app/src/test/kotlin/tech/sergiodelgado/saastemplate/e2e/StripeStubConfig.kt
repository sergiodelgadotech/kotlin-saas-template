package tech.sergiodelgado.saastemplate.e2e

import io.mockk.every
import io.mockk.mockk
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@TestConfiguration
class StripeStubConfig {

    companion object {
        const val CHECKOUT_PATH = "/__stub/checkout"
        const val PORTAL_PATH = "/__stub/portal"

        val DEV_ORG_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val noSubscription: BillingService = mockk {
            every { currentSubscription() } returns null
            every { createCheckoutSession(any()) } returns CHECKOUT_PATH
            every { createPortalSession() } returns PORTAL_PATH
        }

        val starterSubscription: BillingService = mockk {
            every { currentSubscription() } returns Subscription(
                id = UUID.randomUUID(),
                organizationId = DEV_ORG_ID,
                externalCustomerId = "cus_stub",
                plan = "STARTER",
                status = SubscriptionStatus.ACTIVE,
            )
            every { createCheckoutSession(any()) } returns CHECKOUT_PATH
            every { createPortalSession() } returns PORTAL_PATH
        }
    }

    @Bean
    @Primary
    fun billingService(): BillingService = starterSubscription
}

@RestController
class StripeStubController {
    @GetMapping(StripeStubConfig.CHECKOUT_PATH)
    fun checkout() = mapOf("stub" to "checkout")

    @GetMapping(StripeStubConfig.PORTAL_PATH)
    fun portal() = mapOf("stub" to "portal")
}
