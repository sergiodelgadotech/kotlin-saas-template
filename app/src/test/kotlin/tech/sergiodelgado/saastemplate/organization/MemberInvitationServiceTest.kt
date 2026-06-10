package tech.sergiodelgado.saastemplate.organization

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isNotNull
import strikt.assertions.message
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.DefaultMemberRole
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.OrganizationService
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import tech.sergiodelgado.saasstarter.validation.DomainValidationException
import tech.sergiodelgado.saasstarter.web.ForbiddenException
import java.util.Optional
import java.util.UUID

class MemberInvitationServiceTest {

    private val organizationService = mockk<OrganizationService>(relaxed = true)
    private val memberRepository = mockk<MemberRepository>()
    private val idpUserDirectory = mockk<IdpUserDirectory>()

    private val orgId = UUID.randomUUID()
    private val callerUserId = "caller-sub-123"

    private fun serviceWith(idp: IdpUserDirectory?) = MemberInvitationService(
        organizationService = organizationService,
        memberRepository = memberRepository,
        idpUserDirectory = Optional.ofNullable(idp),
    )

    private val service = serviceWith(idpUserDirectory)

    private fun callerMember(role: DefaultMemberRole) = Member(
        organizationId = orgId,
        externalUserId = callerUserId,
        role = role.name,
    )

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
        SecurityContextHolder.clearContext()
    }

    private fun setupContext(callerRole: DefaultMemberRole) {
        TenantContext.set(orgId)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(callerUserId, null, emptyList())
        every { memberRepository.findByExternalUserId(callerUserId) } returns callerMember(callerRole)
    }

    // ── Role checks ──────────────────────────────────────────────────────────

    @Test
    fun `caller with role MEMBER throws ForbiddenException`() {
        setupContext(DefaultMemberRole.MEMBER)

        assertThrows<ForbiddenException> {
            service.invite("alice@example.com", "MEMBER")
        }
    }

    @Test
    fun `caller who is not a member of this org throws ForbiddenException`() {
        TenantContext.set(orgId)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(callerUserId, null, emptyList())
        every { memberRepository.findByExternalUserId(callerUserId) } returns null

        assertThrows<ForbiddenException> {
            service.invite("alice@example.com", "MEMBER")
        }
    }

    @Test
    fun `caller who is a member of a different org throws ForbiddenException`() {
        TenantContext.set(orgId)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(callerUserId, null, emptyList())
        val otherOrgId = UUID.randomUUID()
        every { memberRepository.findByExternalUserId(callerUserId) } returns Member(
            organizationId = otherOrgId,
            externalUserId = callerUserId,
            role = DefaultMemberRole.OWNER.name,
        )

        assertThrows<ForbiddenException> {
            service.invite("alice@example.com", "MEMBER")
        }
    }

    @Test
    fun `caller with role OWNER is allowed to invite`() {
        setupContext(DefaultMemberRole.OWNER)
        every { idpUserDirectory.findOrInvite(any()) } returns "new-user-sub"

        service.invite("alice@example.com", "MEMBER")

        verify { organizationService.inviteMember("new-user-sub", "MEMBER") }
    }

    @Test
    fun `caller with role ADMIN is allowed to invite`() {
        setupContext(DefaultMemberRole.ADMIN)
        every { idpUserDirectory.findOrInvite(any()) } returns "new-user-sub"

        service.invite("alice@example.com", "MEMBER")

        verify { organizationService.inviteMember("new-user-sub", "MEMBER") }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    fun `invalid email throws DomainValidationException`() {
        setupContext(DefaultMemberRole.OWNER)

        assertThrows<DomainValidationException> {
            service.invite("not-an-email", "MEMBER")
        }
    }

    @Test
    fun `role OWNER is rejected by validation`() {
        setupContext(DefaultMemberRole.OWNER)

        assertThrows<DomainValidationException> {
            service.invite("alice@example.com", "OWNER")
        }
    }

    // ── IdP directory not configured ─────────────────────────────────────────

    @Test
    fun `null idpUserDirectory throws IllegalStateException with helpful message`() {
        val serviceWithoutIdp = serviceWith(null)
        setupContext(DefaultMemberRole.OWNER)

        val ex = assertThrows<IllegalStateException> {
            serviceWithoutIdp.invite("alice@example.com", "MEMBER")
        }
        expectThat(ex).message.isNotNull().contains("IdpUserDirectory")
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `successful invite calls findOrInvite and inviteMember with correct args`() {
        setupContext(DefaultMemberRole.OWNER)
        val returnedSub = "zitadel-sub-xyz"
        every { idpUserDirectory.findOrInvite("alice@example.com") } returns returnedSub

        service.invite("alice@example.com", "ADMIN")

        verify(exactly = 1) { idpUserDirectory.findOrInvite("alice@example.com") }
        verify(exactly = 1) { organizationService.inviteMember(returnedSub, "ADMIN") }
    }
}
