package tech.sergiodelgado.saastemplate.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import tech.sergiodelgado.saastemplate.account.AvatarImage
import tech.sergiodelgado.saastemplate.account.AvatarSource
import tech.sergiodelgado.saastemplate.account.UserAccountService
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.time.Instant
import java.util.UUID

class NavModelAdviceTest {

    private val memberRepository = mockk<MemberRepository>()
    private val userAccountService = mockk<UserAccountService>()
    private val advice = NavModelAdvice(memberRepository, userAccountService)

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }

    private fun oidcUser(subject: String, email: String) = mockk<OidcUser> {
        every { this@mockk.subject } returns subject
        every { this@mockk.email } returns email
    }

    private fun member(firstName: String?, lastName: String?, avatarUrl: String? = null) = Member(
        organizationId = UUID.randomUUID(),
        externalUserId = "test-sub",
        firstName = firstName,
        lastName = lastName,
        avatarUrl = avatarUrl,
    )

    // navInitials

    @Test
    fun `navInitials returns question mark when principal is null`() {
        expectThat(advice.navInitials(null)).isEqualTo("?")
    }

    @Test
    fun `navInitials returns uppercase initials from members table`() {
        val principal = oidcUser("sub", "a@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns member("Alice", "Smith")
        expectThat(advice.navInitials(principal)).isEqualTo("AS")
    }

    @Test
    fun `navInitials falls back to first letter of OIDC email when no member found`() {
        val principal = oidcUser("sub", "alice@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns null
        expectThat(advice.navInitials(principal)).isEqualTo("A")
    }

    // navDisplayName

    @Test
    fun `navDisplayName returns empty string when principal is null`() {
        expectThat(advice.navDisplayName(null)).isEqualTo("")
    }

    @Test
    fun `navDisplayName returns full name from members table`() {
        val principal = oidcUser("sub", "a@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns member("Alice", "Smith")
        expectThat(advice.navDisplayName(principal)).isEqualTo("Alice Smith")
    }

    @Test
    fun `navDisplayName falls back to OIDC email when member has no name`() {
        val principal = oidcUser("sub", "alice@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns member(null, null)
        expectThat(advice.navDisplayName(principal)).isEqualTo("alice@example.com")
    }

    // navEmail

    @Test
    fun `navEmail returns empty string when principal is null`() {
        expectThat(advice.navEmail(null)).isEqualTo("")
    }

    @Test
    fun `navEmail returns email from OIDC principal`() {
        val principal = oidcUser("sub", "alice@example.com")
        expectThat(advice.navEmail(principal)).isEqualTo("alice@example.com")
    }

    // navAvatarUrl

    @Test
    fun `navAvatarUrl returns null when principal is null`() {
        expectThat(advice.navAvatarUrl(null)).isEqualTo(null)
    }

    @Test
    fun `navAvatarUrl returns versioned proxy path when stored binary image exists`() {
        // TenantContext must be present so the NavModelAdvice guard allows getStoredAvatar to run.
        TenantContext.set(UUID.randomUUID())
        val updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
        val stored = AvatarImage(
            id = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            externalUserId = "sub",
            contentType = "image/png",
            bytes = byteArrayOf(1, 2, 3),
            source = AvatarSource.IDP.name,
            updatedAt = updatedAt,
        )
        val principal = oidcUser("sub", "a@example.com")
        every { userAccountService.getStoredAvatar("sub") } returns stored

        expectThat(advice.navAvatarUrl(principal)).isEqualTo("/avatar?v=1700000000000")
    }

    @Test
    fun `navAvatarUrl returns plain proxy path when only external URL exists`() {
        val principal = oidcUser("sub", "a@example.com")
        every { userAccountService.getStoredAvatar("sub") } returns null
        every { memberRepository.findByExternalUserId("sub") } returns member("Alice", "Smith", "https://example.com/avatar.jpg")
        expectThat(advice.navAvatarUrl(principal)).isEqualTo("/avatar")
    }

    @Test
    fun `navAvatarUrl returns null when member has no avatar and no stored image`() {
        val principal = oidcUser("sub", "a@example.com")
        every { userAccountService.getStoredAvatar("sub") } returns null
        every { memberRepository.findByExternalUserId("sub") } returns member("Alice", "Smith", null)
        expectThat(advice.navAvatarUrl(principal)).isEqualTo(null)
    }

    @Test
    fun `navAvatarUrl returns null when no member found and no stored image`() {
        val principal = oidcUser("sub", "a@example.com")
        every { userAccountService.getStoredAvatar("sub") } returns null
        every { memberRepository.findByExternalUserId("sub") } returns null
        expectThat(advice.navAvatarUrl(principal)).isEqualTo(null)
    }
}
