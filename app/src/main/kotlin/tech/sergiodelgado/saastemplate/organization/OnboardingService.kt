package tech.sergiodelgado.saastemplate.organization

import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.DefaultBillingPlan
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import tech.sergiodelgado.saasstarter.organization.DefaultMemberRole
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import kotlin.random.Random

@Service
@Transactional
class OnboardingService(
    private val organizationRepository: OrganizationRepository,
    private val memberRepository: MemberRepository,
    private val billingService: BillingService,
    private val subscriptionRepository: SubscriptionRepository,
) {
    // Evict the cached null that ZitadelAuthenticationSuccessHandler wrote during login
    // (MemberRepository.findOrganizationIdByUserId is @Cacheable and caches null).
    @CacheEvict(cacheNames = ["tenant-by-user"], key = "#ownerUserId")
    fun createOrganization(
        ownerUserId: String,
        name: String,
        email: String = "",
        firstName: String? = null,
        lastName: String? = null,
    ): Organization {
        val org = organizationRepository.save(Organization(name = name, slug = slugFor(name)))
        memberRepository.save(
            Member(
                organizationId = org.id,
                externalUserId = ownerUserId,
                role = DefaultMemberRole.OWNER.name,
                email = email,
                firstName = firstName,
                lastName = lastName,
            )
        )
        // Billing is deferred to ensureBilling (called at plan-selection step) so that
        // abandoning the org-name step doesn't leave an orphaned Stripe customer, and so
        // the plan page re-appears on the next login if the user never chose a plan.
        return org
    }

    // Idempotent: for STARTER, finds or creates a local subscription with no Stripe customer.
    // For paid plans, delegates to BillingService.ensureStripeCustomer which handles all cases:
    // new subscription, upgrade from STARTER, or already set up.
    fun ensureBilling(planName: String = DefaultBillingPlan.STARTER.name): Subscription {
        val organizationId = TenantContext.get()

        if (planName == DefaultBillingPlan.STARTER.name) {
            subscriptionRepository.findByOrganizationId(organizationId)?.let { return it }
            return subscriptionRepository.save(
                Subscription(
                    organizationId = organizationId,
                    plan = DefaultBillingPlan.STARTER.name,
                    status = SubscriptionStatus.ACTIVE,
                )
            )
        }

        return billingService.ensureStripeCustomer()
    }

    private fun slugFor(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        val suffix = (1..6).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        return "$base-$suffix"
    }
}
