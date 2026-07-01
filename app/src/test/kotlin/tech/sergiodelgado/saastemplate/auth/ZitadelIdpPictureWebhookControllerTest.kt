package tech.sergiodelgado.saastemplate.auth

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import tech.sergiodelgado.saasstarter.organization.MemberRepository

class ZitadelIdpPictureWebhookControllerTest {

    private val memberRepository = mockk<MemberRepository>()
    private val controller = ZitadelIdpPictureWebhookController(memberRepository)

    @Test
    fun `updates avatar when picture present in raw IDP information`() {
        every { memberRepository.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpPicture(
            IdpPicturePayload(
                userId = "user-abc",
                idpInformation = IdpInformation(rawInformation = mapOf("picture" to "https://example.com/avatar.jpg")),
            )
        )

        verify { memberRepository.updateAvatarUrl("user-abc", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `skips avatar update when raw IDP information has no picture`() {
        controller.handleIdpPicture(
            IdpPicturePayload(
                userId = "user-abc",
                idpInformation = IdpInformation(rawInformation = mapOf("email" to "user@example.com")),
            )
        )

        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `skips avatar update when idpInformation is null`() {
        controller.handleIdpPicture(IdpPicturePayload(userId = "user-abc", idpInformation = null))

        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `skips avatar update when userId is null`() {
        controller.handleIdpPicture(
            IdpPicturePayload(
                userId = null,
                idpInformation = IdpInformation(rawInformation = mapOf("picture" to "https://example.com/avatar.jpg")),
            )
        )

        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
    }
}
