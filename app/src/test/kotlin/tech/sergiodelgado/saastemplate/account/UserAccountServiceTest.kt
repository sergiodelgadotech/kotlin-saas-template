package tech.sergiodelgado.saastemplate.account

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
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

        io.mockk.verify(exactly = 0) { memberRepository.updateProfile(any(), any(), any(), any()) }
    }
}
