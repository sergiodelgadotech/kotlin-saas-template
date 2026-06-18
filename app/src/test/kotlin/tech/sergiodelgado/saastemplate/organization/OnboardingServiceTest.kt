package tech.sergiodelgado.saastemplate.organization

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.matches
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
import java.util.Optional
import java.util.UUID

class OnboardingServiceTest {

    private val organizationRepository = mockk<OrganizationRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val billingService = mockk<BillingService>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()

    private val service = OnboardingService(organizationRepository, memberRepository, billingService, subscriptionRepository)

    private val orgSlot = slot<Organization>()
    private val memberSlot = slot<Member>()

    private fun stubOrgSave() {
        every { organizationRepository.save(capture(orgSlot)) } answers { orgSlot.captured }
        every { memberRepository.save(capture(memberSlot)) } answers { memberSlot.captured }
    }

    @Test
    fun `createOrganization saves org with name and slug`() {
        stubOrgSave()

        service.createOrganization("user-sub", "Acme Inc", "ceo@acme.com")

        expectThat(orgSlot.captured.name).isEqualTo("Acme Inc")
        expectThat(orgSlot.captured.slug).matches(Regex("acme-inc-[0-9a-f]{6}"))
    }

    @Test
    fun `createOrganization saves owner member with OIDC profile`() {
        stubOrgSave()

        service.createOrganization("user-sub", "Acme", "ceo@acme.com", "Alice", "Smith")

        with(memberSlot.captured) {
            expectThat(externalUserId).isEqualTo("user-sub")
            expectThat(role).isEqualTo(DefaultMemberRole.OWNER.name)
            expectThat(email).isEqualTo("ceo@acme.com")
            expectThat(firstName).isEqualTo("Alice")
            expectThat(lastName).isEqualTo("Smith")
        }
    }

    @Test
    fun `createOrganization does not create a Stripe customer`() {
        stubOrgSave()

        service.createOrganization("user-sub", "Acme", "ceo@acme.com")

        verify(exactly = 0) { billingService.createCustomer(any(), any(), any()) }
    }

    @Test
    fun `createOrganization does not create a subscription`() {
        stubOrgSave()

        service.createOrganization("user-sub", "Acme", "ceo@acme.com")

        verify(exactly = 0) { billingService.ensureSubscription(any(), any()) }
    }

    // --- ensureBilling ---

    private val testOrgId = UUID.randomUUID()

    @BeforeEach
    fun setTenantContext() {
        TenantContext.set(testOrgId)
    }

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }
    private val testOrg = Organization(id = testOrgId, name = "Acme", slug = "acme-123456")
    private val ownerMember = Member(
        organizationId = testOrgId,
        externalUserId = "user-sub",
        role = DefaultMemberRole.OWNER.name,
        email = "ceo@acme.com",
    )

    @Test
    fun `ensureBilling creates customer and TRIALING subscription when none exists`() {
        every { subscriptionRepository.findByOrganizationId(testOrgId) } returns null
        every { organizationRepository.findById(testOrgId) } returns Optional.of(testOrg)
        every { memberRepository.findByOrganizationId(testOrgId) } returns listOf(ownerMember)
        every { billingService.createCustomer(testOrgId, email = "ceo@acme.com", name = "Acme") } returns "cus_test"
        every { billingService.ensureSubscription(testOrgId, "cus_test") } returns Subscription(
            organizationId = testOrgId,
            externalCustomerId = "cus_test",
            plan = DefaultBillingPlan.STARTER.name,
            status = SubscriptionStatus.TRIALING,
        )

        service.ensureBilling()

        verify(exactly = 1) { billingService.createCustomer(testOrgId, email = "ceo@acme.com", name = "Acme") }
        verify(exactly = 1) { billingService.ensureSubscription(testOrgId, "cus_test") }
    }

    @Test
    fun `ensureBilling returns existing subscription without creating customer`() {
        val existing = Subscription(
            organizationId = testOrgId,
            externalCustomerId = "cus_existing",
            plan = DefaultBillingPlan.STARTER.name,
            status = SubscriptionStatus.TRIALING,
        )
        every { subscriptionRepository.findByOrganizationId(testOrgId) } returns existing

        val result = service.ensureBilling()

        expectThat(result).isEqualTo(existing)
        verify(exactly = 0) { billingService.createCustomer(any(), any(), any()) }
        verify(exactly = 0) { billingService.ensureSubscription(any(), any()) }
    }
}
