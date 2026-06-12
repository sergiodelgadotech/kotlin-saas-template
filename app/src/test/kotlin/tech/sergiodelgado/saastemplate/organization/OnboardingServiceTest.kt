package tech.sergiodelgado.saastemplate.organization

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.matches
import tech.sergiodelgado.saasstarter.billing.BillingService
import tech.sergiodelgado.saasstarter.billing.DefaultBillingPlan
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionStatus
import tech.sergiodelgado.saasstarter.organization.DefaultMemberRole
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import java.util.UUID

class OnboardingServiceTest {

    private val organizationRepository = mockk<OrganizationRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val billingService = mockk<BillingService>()

    private val service = OnboardingService(organizationRepository, memberRepository, billingService)

    private val orgSlot = slot<Organization>()
    private val memberSlot = slot<Member>()

    private fun stubSave() {
        every { organizationRepository.save(capture(orgSlot)) } answers { orgSlot.captured }
        every { memberRepository.save(capture(memberSlot)) } answers { memberSlot.captured }
        every { billingService.createCustomer(any(), any(), any(), any()) } returns "cus_test"
        every { billingService.ensureSubscription(any(), any()) } returns Subscription(
            organizationId = UUID.randomUUID(),
            externalCustomerId = "cus_test",
            plan = DefaultBillingPlan.STARTER.name,
            status = SubscriptionStatus.TRIALING,
        )
    }

    @Test
    fun `createOrganization saves org with name and slug`() {
        stubSave()

        service.createOrganization("user-sub", "Acme Inc", "ceo@acme.com")

        expectThat(orgSlot.captured.name).isEqualTo("Acme Inc")
        expectThat(orgSlot.captured.slug).matches(Regex("acme-inc-[0-9a-f]{6}"))
    }

    @Test
    fun `createOrganization saves owner member with OIDC profile`() {
        stubSave()

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
    fun `createOrganization creates a Stripe customer for the new org`() {
        stubSave()

        service.createOrganization("user-sub", "Acme", "ceo@acme.com", "Alice", null)

        verify(exactly = 1) {
            billingService.createCustomer(
                organizationId = orgSlot.captured.id,
                email = "ceo@acme.com",
                name = "Acme",
            )
        }
    }

    @Test
    fun `createOrganization creates a TRIALING subscription with the customer ID`() {
        stubSave()

        service.createOrganization("user-sub", "Acme", "ceo@acme.com")

        verify(exactly = 1) {
            billingService.ensureSubscription(
                organizationId = orgSlot.captured.id,
                customerId = "cus_test",
            )
        }
    }
}
