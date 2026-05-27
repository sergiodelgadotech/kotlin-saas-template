package tech.sergiodelgado.saastemplate.integration

import com.stripe.Stripe
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.billing.StripeWebhookHandler
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class BillingAutoConfigSmokeTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Test
    fun `BillingService and StripeWebhookHandler are wired from starter`() {
        expectThat(context.getBeansOfType(BillingService::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(StripeWebhookHandler::class.java)).hasSize(1)
    }

    @Test
    fun `Stripe apiKey is set from billing properties`() {
        // Provided in application-test.yml — should be non-empty
        expectThat(Stripe.apiKey).isNotEmpty()
    }

    @Test
    fun `subscription can be persisted with externalCustomerId`() {
        val org = organizationRepository.save(Organization(name = "Smoke Org", slug = "smoke-billing"))
        val sub = subscriptionRepository.save(
            Subscription(organizationId = org.id, externalCustomerId = "cus_test_smoke")
        )
        val reloaded = subscriptionRepository.findByExternalCustomerId("cus_test_smoke")
        expectThat(reloaded?.id).isEqualTo(sub.id)
    }
}
