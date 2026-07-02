package tech.sergiodelgado.saastemplate.auth

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import tech.sergiodelgado.saastemplate.account.UserAccountService

class ZitadelIdpIntentWebhookControllerTest {

    private val userAccountService = mockk<UserAccountService>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val controller = ZitadelIdpIntentWebhookController(userAccountService)

    // ── helpers ─────────────────────────────────────────────────────────────────

    /** Build the full Zitadel execution envelope as the controller receives it. */
    private fun envelope(
        userId: String? = "user-abc",
        username: String? = null,
        email: String? = null,
        picture: String? = null,
        avatarUrl: String? = null,
        nestedUnderUserKey: Boolean = true,
        extraResponseField: String? = null,
    ): ObjectNode {
        val rawInfo = objectMapper.createObjectNode()
        if (nestedUnderUserKey) {
            val userNode = objectMapper.createObjectNode()
            email?.let { userNode.put("email", it) }
            picture?.let { userNode.put("picture", it) }
            avatarUrl?.let { userNode.put("avatar_url", it) }
            rawInfo.set("User", userNode)
        } else {
            email?.let { rawInfo.put("email", it) }
            picture?.let { rawInfo.put("picture", it) }
            avatarUrl?.let { rawInfo.put("avatar_url", it) }
        }

        val idpInfo = objectMapper.createObjectNode()
        username?.let { idpInfo.put("username", it) }
        idpInfo.set("rawInformation", rawInfo)

        val response = objectMapper.createObjectNode()
        userId?.let { response.put("userId", it) }
        response.set("idpInformation", idpInfo)
        extraResponseField?.let { response.put("extra", it) }

        return objectMapper.createObjectNode().apply {
            set("response", response)
        }
    }

    private fun responseNode(result: Any?) =
        (result as? org.springframework.http.ResponseEntity<*>)?.body

    // ── username injection ───────────────────────────────────────────────────────

    @Test
    fun `injects username from nested User email when username is absent`() {
        val result = controller.handleIdpIntent(
            envelope(email = "slack-user@example.com")
        )

        val body = result.body as? ObjectNode
        expectThat(body?.path("idpInformation")?.path("username")?.asText())
            .isEqualTo("slack-user@example.com")
    }

    @Test
    fun `injects username from nested User email when username is blank`() {
        val result = controller.handleIdpIntent(
            envelope(username = "  ", email = "slack-user@example.com")
        )

        val body = result.body as? ObjectNode
        expectThat(body?.path("idpInformation")?.path("username")?.asText())
            .isEqualTo("slack-user@example.com")
    }

    @Test
    fun `does not overwrite an already-set username`() {
        val result = controller.handleIdpIntent(
            envelope(username = "existing-user", email = "other@example.com")
        )

        val body = result.body as? ObjectNode
        expectThat(body?.path("idpInformation")?.path("username")?.asText())
            .isEqualTo("existing-user")
    }

    @Test
    fun `injects username from top-level rawInformation email when no User key`() {
        val result = controller.handleIdpIntent(
            envelope(email = "user@example.com", nestedUnderUserKey = false)
        )

        val body = result.body as? ObjectNode
        expectThat(body?.path("idpInformation")?.path("username")?.asText())
            .isEqualTo("user@example.com")
    }

    @Test
    fun `leaves username absent when no email is available`() {
        val result = controller.handleIdpIntent(
            envelope(username = null, email = null)
        )

        val body = result.body as? ObjectNode
        // username node should not have been injected
        expectThat(body?.path("idpInformation")?.has("username")).isEqualTo(false)
    }

    // ── avatar capture ───────────────────────────────────────────────────────────

    @Test
    fun `updates avatar from nested User picture in rawInformation`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpIntent(
            envelope(picture = "https://example.com/avatar.jpg")
        )

        verify { userAccountService.updateAvatarUrl("user-abc", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `updates avatar from nested User avatar_url in rawInformation`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpIntent(
            envelope(avatarUrl = "https://avatars.githubusercontent.com/u/123")
        )

        verify { userAccountService.updateAvatarUrl("user-abc", "https://avatars.githubusercontent.com/u/123") }
    }

    @Test
    fun `updates avatar from top-level picture in rawInformation`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } just Runs

        controller.handleIdpIntent(
            envelope(picture = "https://example.com/avatar.jpg", nestedUnderUserKey = false)
        )

        verify { userAccountService.updateAvatarUrl("user-abc", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `skips avatar update when rawInformation has no picture`() {
        controller.handleIdpIntent(
            envelope(email = "user@example.com")
        )

        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `skips avatar update when userId is absent`() {
        controller.handleIdpIntent(
            envelope(userId = null, picture = "https://example.com/avatar.jpg")
        )

        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }

    @Test
    fun `avatar update failure does not prevent returning a valid response`() {
        every { userAccountService.updateAvatarUrl(any(), any()) } throws RuntimeException("DB down")

        val result = controller.handleIdpIntent(
            envelope(email = "user@example.com", picture = "https://example.com/avatar.jpg")
        )

        expectThat(result.statusCode.is2xxSuccessful).isEqualTo(true)
        val body = result.body as? ObjectNode
        expectThat(body?.path("idpInformation")?.path("username")?.asText())
            .isEqualTo("user@example.com")
    }

    // ── response passthrough ─────────────────────────────────────────────────────

    @Test
    fun `returns the full response node including fields not modelled by this controller`() {
        val result = controller.handleIdpIntent(
            envelope(email = "user@example.com", extraResponseField = "should-survive")
        )

        val body = result.body as? ObjectNode
        expectThat(body?.path("extra")?.asText()).isEqualTo("should-survive")
    }

    @Test
    fun `returns empty 200 when response node is absent`() {
        val noResponseEnvelope = objectMapper.createObjectNode()

        val result = controller.handleIdpIntent(noResponseEnvelope)

        expectThat(result.statusCode.is2xxSuccessful).isEqualTo(true)
        expectThat(result.body).isNull()
        verify(exactly = 0) { userAccountService.updateAvatarUrl(any(), any()) }
    }
}
