package tech.sergiodelgado.saastemplate.organization

import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.DefaultMemberRole
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import java.util.UUID
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

    // Idempotent: returns the existing subscription if one already exists, so repeated
    // calls (e.g. when the gate bounces the user back to the plan page) are safe.
    fun ensureBilling(organizationId: UUID): Subscription {
        subscriptionRepository.findByOrganizationId(organizationId)?.let { return it }
        val org = requireNotNull(organizationRepository.findById(organizationId).orElse(null)) {
            "Organization $organizationId not found"
        }
        val ownerEmail = memberRepository.findByOrganizationId(organizationId)
            .firstOrNull { it.role == DefaultMemberRole.OWNER.name }?.email.orEmpty()
        val customerId = billingService.createCustomer(organizationId, email = ownerEmail, name = org.name)
        return billingService.ensureSubscription(organizationId, customerId)
    }

    private fun slugFor(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        val suffix = (1..6).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        return "$base-$suffix"
    }
}
