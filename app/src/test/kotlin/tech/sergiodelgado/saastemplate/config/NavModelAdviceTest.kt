package tech.sergiodelgado.saastemplate.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

class NavModelAdviceTest {

    private val memberRepository = mockk<MemberRepository>()
    private val advice = NavModelAdvice(memberRepository)

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
    fun `navAvatarUrl returns avatar URL from members table`() {
        val principal = oidcUser("sub", "a@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns member("Alice", "Smith", "https://example.com/avatar.jpg")
        expectThat(advice.navAvatarUrl(principal)).isEqualTo("https://example.com/avatar.jpg")
    }

    @Test
    fun `navAvatarUrl returns null when member has no avatar`() {
        val principal = oidcUser("sub", "a@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns member("Alice", "Smith", null)
        expectThat(advice.navAvatarUrl(principal)).isEqualTo(null)
    }

    @Test
    fun `navAvatarUrl returns null when no member found`() {
        val principal = oidcUser("sub", "a@example.com")
        every { memberRepository.findByExternalUserId("sub") } returns null
        expectThat(advice.navAvatarUrl(principal)).isEqualTo(null)
    }
}
