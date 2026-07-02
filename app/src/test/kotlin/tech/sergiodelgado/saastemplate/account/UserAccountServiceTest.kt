package tech.sergiodelgado.saastemplate.account

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

class UserAccountServiceTest {

    private val idpUserDirectory = mockk<IdpUserDirectory>()
    private val memberRepository = mockk<MemberRepository>()
    private val service = UserAccountService(idpUserDirectory, memberRepository)

    @Test
    fun `getProfile returns firstName and lastName from member row`() {
        val orgId = UUID.randomUUID()
        every { memberRepository.findByExternalUserId("user-123") } returns Member(
            organizationId = orgId,
            externalUserId = "user-123",
            firstName = "Alice",
            lastName = "Smith",
        )

        val profile = service.getProfile("user-123")

        expectThat(profile.firstName).isEqualTo("Alice")
        expectThat(profile.lastName).isEqualTo("Smith")
    }

    @Test
    fun `getProfile returns empty strings when member row not found`() {
        every { memberRepository.findByExternalUserId("unknown") } returns null

        val profile = service.getProfile("unknown")

        expectThat(profile.firstName).isEqualTo("")
        expectThat(profile.lastName).isEqualTo("")
    }

    @Test
    fun `updateDisplayName calls IdP first then local repo`() {
        justRun { idpUserDirectory.updateProfile(any(), any(), any()) }
        justRun { memberRepository.updateProfile(any(), any(), any(), any()) }

        service.updateDisplayName("user-123", "Alice", "Smith", "alice@example.com")

        verifyOrder {
            idpUserDirectory.updateProfile("user-123", "Alice", "Smith")
            memberRepository.updateProfile("user-123", "alice@example.com", "Alice", "Smith")
        }
    }

    @Test
    fun `updateDisplayName does not call local repo when IdP throws`() {
        every { idpUserDirectory.updateProfile(any(), any(), any()) } throws
            IllegalStateException("Zitadel error")

        assertThrows<IllegalStateException> {
            service.updateDisplayName("user-123", "Alice", "Smith", "alice@example.com")
        }

        verify(exactly = 0) { memberRepository.updateProfile(any(), any(), any(), any()) }
    }

    // ── syncAvatarFromIdp ────────────────────────────────────────────────────────

    private fun member(userId: String) = Member(
        organizationId = UUID.randomUUID(),
        externalUserId = userId,
    )

    @Test
    fun `syncAvatarFromIdp updates repository immediately when member row exists`() {
        every { memberRepository.findByExternalUserId("user-123") } returns member("user-123")
        justRun { memberRepository.updateAvatarUrl("user-123", any()) }

        service.syncAvatarFromIdp("user-123", "https://example.com/pic.jpg")

        verify(exactly = 1) { memberRepository.updateAvatarUrl("user-123", "https://example.com/pic.jpg") }
        expectThat(service.consumePendingAvatarUrl("user-123")).isNull()
    }

    @Test
    fun `syncAvatarFromIdp buffers URL when member row does not exist yet`() {
        every { memberRepository.findByExternalUserId("new-user") } returns null

        service.syncAvatarFromIdp("new-user", "https://example.com/pic.jpg")

        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
        expectThat(service.consumePendingAvatarUrl("new-user"))
            .isEqualTo("https://example.com/pic.jpg")
    }

    @Test
    fun `consumePendingAvatarUrl returns null when nothing buffered`() {
        expectThat(service.consumePendingAvatarUrl("unknown")).isNull()
    }

    @Test
    fun `consumePendingAvatarUrl drains the entry so second call returns null`() {
        every { memberRepository.findByExternalUserId("new-user") } returns null

        service.syncAvatarFromIdp("new-user", "https://example.com/pic.jpg")
        service.consumePendingAvatarUrl("new-user")

        expectThat(service.consumePendingAvatarUrl("new-user")).isNull()
    }
}
