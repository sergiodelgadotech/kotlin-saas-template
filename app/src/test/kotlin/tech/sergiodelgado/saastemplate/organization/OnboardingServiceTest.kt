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
import strikt.assertions.isNull
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
    fun `ensureBilling for STARTER does not call Stripe`() {
        every { subscriptionRepository.findByOrganizationId(testOrgId) } returns null
        every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }

        service.ensureBilling(DefaultBillingPlan.STARTER.name)

        verify(exactly = 0) { billingService.createCustomer(any(), any(), any()) }
        verify(exactly = 0) { billingService.ensureSubscription(any(), any()) }
    }

    @Test
    fun `ensureBilling for STARTER saves subscription with null externalCustomerId and TRIALING status`() {
        every { subscriptionRepository.findByOrganizationId(testOrgId) } returns null
        val saved = slot<Subscription>()
        every { subscriptionRepository.save(capture(saved)) } answers { firstArg() }

        service.ensureBilling(DefaultBillingPlan.STARTER.name)

        expectThat(saved.captured.externalCustomerId).isNull()
        expectThat(saved.captured.plan).isEqualTo(DefaultBillingPlan.STARTER.name)
        expectThat(saved.captured.status).isEqualTo(SubscriptionStatus.TRIALING)
        expectThat(saved.captured.organizationId).isEqualTo(testOrgId)
    }

    @Test
    fun `ensureBilling for paid plan delegates to billingService ensureStripeCustomer`() {
        val sub = Subscription(organizationId = testOrgId, externalCustomerId = "cus_test")
        every { billingService.ensureStripeCustomer() } returns sub

        val result = service.ensureBilling(DefaultBillingPlan.PRO.name)

        expectThat(result).isEqualTo(sub)
        verify(exactly = 1) { billingService.ensureStripeCustomer() }
    }
}
