package tech.sergiodelgado.saastemplate.auth

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import tech.sergiodelgado.saastemplate.account.UserAccountService

class ZitadelIdpPictureWebhookControllerTest {

    private val userAccountService = mockk<UserAccountService>()
    private val controller = ZitadelIdpPictureWebhookController(userAccountService)

    private fun payload(userId: String?, rawInformation: Map<String, Any?>?) = ZitadelExecutionPayload(
        response = IdpIntentResponse(
            userId = userId,
            idpInformation = rawInformation?.let { IdpInformation(rawInformation = it) },
        ),
    )

    @Test
    fun `updates avatar from Google-style nested User key in rawInformation`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpPicture(
            payload("user-abc", mapOf("User" to mapOf("picture" to "https://example.com/avatar.jpg")))
        )

        verify { userAccountService.updateAvatarUrl("user-abc", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `updates avatar from GitHub-style nested User avatar_url in rawInformation`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpPicture(
            payload("user-abc", mapOf("User" to mapOf("avatar_url" to "https://avatars.githubusercontent.com/u/123")))
        )

        verify { userAccountService.updateAvatarUrl("user-abc", "https://avatars.githubusercontent.com/u/123") }
    }

    @Test
    fun `updates avatar from top-level picture in rawInformation`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpPicture(
            payload("user-abc", mapOf("picture" to "https://example.com/avatar.jpg"))
        )

        verify { userAccountService.updateAvatarUrl("user-abc", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `skips avatar update when rawInformation has no picture`() {
        controller.handleIdpPicture(
            payload("user-abc", mapOf("email" to "user@example.com"))
        )

        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `skips avatar update when idpInformation is null`() {
        controller.handleIdpPicture(payload("user-abc", null))

        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `skips avatar update when response userId is null`() {
        controller.handleIdpPicture(
            payload(null, mapOf("User" to mapOf("picture" to "https://example.com/avatar.jpg")))
        )

        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `skips avatar update when response is null`() {
        controller.handleIdpPicture(ZitadelExecutionPayload(response = null))

        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }
}
