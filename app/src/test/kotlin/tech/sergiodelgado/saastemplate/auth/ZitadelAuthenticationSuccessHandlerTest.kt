package tech.sergiodelgado.saastemplate.auth

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import tech.sergiodelgado.saastemplate.account.AvatarSource
import tech.sergiodelgado.saastemplate.account.PendingAvatarBytes
import tech.sergiodelgado.saastemplate.account.UserAccountService
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

class ZitadelAuthenticationSuccessHandlerTest {

    private val memberRepository = mockk<MemberRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val userAccountService = mockk<UserAccountService>()
    private val handler = ZitadelAuthenticationSuccessHandler(memberRepository, subscriptionRepository, userAccountService)
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()

    private val orgIdStr = "00000000-0000-0000-0000-000000000001"
    private val orgId = UUID.fromString(orgIdStr)

    private fun authToken(
        subject: String,
        email: String? = "user@example.com",
        givenName: String? = "Test",
        familyName: String? = "User",
        picture: String? = null,
    ): OAuth2AuthenticationToken {
        val oidcUser = mockk<OidcUser> {
            every { this@mockk.subject } returns subject
            every { this@mockk.email } returns email
            every { this@mockk.givenName } returns givenName
            every { this@mockk.familyName } returns familyName
            every { this@mockk.picture } returns picture
        }
        return OAuth2AuthenticationToken(oidcUser, emptyList(), "zitadel")
    }

    private fun stubLoginSync(subject: String) {
        every { memberRepository.updateProfile(subject, any<String>(), any(), any()) } just Runs
        every { memberRepository.updateAvatarUrl(subject, any()) } just Runs
        every { userAccountService.consumePendingAvatarBytes(any()) } returns null
        every { userAccountService.storeAvatar(any(), any(), any(), any()) } just Runs
    }

    @Test
    fun `redirects to dashboard when member and subscription exist`() {
        stubLoginSync("user-abc")
        every { memberRepository.findOrganizationIdByUserId("user-abc") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        every { userAccountService.consumePendingAvatarUrl(any()) } returns null

        handler.onAuthenticationSuccess(request, response, authToken("user-abc"))

        expectThat(response.redirectedUrl).isEqualTo("/dashboard")
    }

    @Test
    fun `redirects to onboarding organization when member does not exist`() {
        stubLoginSync("new-user")
        every { memberRepository.findOrganizationIdByUserId("new-user") } returns null

        handler.onAuthenticationSuccess(request, response, authToken("new-user"))

        expectThat(response.redirectedUrl).isEqualTo("/onboarding/organization")
    }

    @Test
    fun `redirects to onboarding plan when org exists but subscription does not`() {
        stubLoginSync("partial-user")
        every { memberRepository.findOrganizationIdByUserId("partial-user") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns null
        every { userAccountService.consumePendingAvatarUrl(any()) } returns null

        handler.onAuthenticationSuccess(request, response, authToken("partial-user"))

        expectThat(response.redirectedUrl).isEqualTo("/onboarding/plan")
    }

    @Test
    fun `updates member profile from OIDC claims on login`() {
        stubLoginSync("user-xyz")
        every { memberRepository.findOrganizationIdByUserId("user-xyz") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        every { userAccountService.consumePendingAvatarUrl(any()) } returns null

        handler.onAuthenticationSuccess(request, response, authToken("user-xyz", "jane@example.com", "Jane", "Doe"))

        verify { memberRepository.updateProfile("user-xyz", "jane@example.com", "Jane", "Doe") }
    }

    @Test
    fun `syncs OIDC picture claim to avatar when oidcUser has picture and member exists`() {
        stubLoginSync("user-pic")
        every { memberRepository.findOrganizationIdByUserId("user-pic") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        // No buffered avatar — picture comes from the OIDC claim
        every { userAccountService.consumePendingAvatarUrl("user@example.com") } returns null

        handler.onAuthenticationSuccess(
            request, response,
            authToken("user-pic", picture = "https://example.com/avatar.jpg"),
        )

        verify { memberRepository.updateAvatarUrl("user-pic", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `sets avatar from IDP webhook buffer for returning user`() {
        stubLoginSync("returning-user")
        every { memberRepository.findOrganizationIdByUserId("returning-user") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        every { userAccountService.consumePendingAvatarUrl("user@example.com") } returns "https://lh3.googleusercontent.com/avatar.jpg"

        handler.onAuthenticationSuccess(request, response, authToken("returning-user", picture = null))

        verify { memberRepository.updateAvatarUrl("returning-user", "https://lh3.googleusercontent.com/avatar.jpg") }
    }

    @Test
    fun `buffers OIDC picture for new users when available (e g GitHub)`() {
        stubLoginSync("new-user")
        every { memberRepository.findOrganizationIdByUserId("new-user") } returns null
        every { userAccountService.syncAvatarFromIdp(any(), any()) } just Runs

        handler.onAuthenticationSuccess(
            request, response,
            authToken("new-user", email = "user@example.com", picture = "https://avatars.github.com/u/123"),
        )

        // No direct DB write — member row doesn't exist yet
        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
        // Buffer it under email for onboarding to consume
        verify { userAccountService.syncAvatarFromIdp("user@example.com", "https://avatars.github.com/u/123") }
    }

    @Test
    fun `skips avatar for new users when oidcUser has no picture`() {
        stubLoginSync("new-user")
        every { memberRepository.findOrganizationIdByUserId("new-user") } returns null

        handler.onAuthenticationSuccess(request, response, authToken("new-user", picture = null))

        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
        verify(exactly = 0) { userAccountService.syncAvatarFromIdp(any(), any()) }
        verify(exactly = 0) { userAccountService.consumePendingAvatarUrl(any()) }
    }

    @Test
    fun `skips avatar update when no picture and no pending avatar for returning user`() {
        stubLoginSync("user-no-pic")
        every { memberRepository.findOrganizationIdByUserId("user-no-pic") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        every { userAccountService.consumePendingAvatarUrl("user@example.com") } returns null

        handler.onAuthenticationSuccess(request, response, authToken("user-no-pic", picture = null))

        verify(exactly = 0) { memberRepository.updateAvatarUrl(any(), any()) }
    }

    // ── Binary avatar drain (e.g. Microsoft/Entra returning users) ───────────────

    @Test
    fun `stores binary avatar from IDP webhook buffer for returning user (e g Microsoft)`() {
        val bytes = byteArrayOf(1, 2, 3)
        val pending = PendingAvatarBytes("image/jpeg", bytes, AvatarSource.IDP)
        stubLoginSync("ms-user")
        every { memberRepository.findOrganizationIdByUserId("ms-user") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        every { userAccountService.consumePendingAvatarUrl("user@example.com") } returns null
        every { userAccountService.consumePendingAvatarBytes("user@example.com") } returns pending

        handler.onAuthenticationSuccess(request, response, authToken("ms-user", picture = null))

        verify { userAccountService.storeAvatar("ms-user", "image/jpeg", bytes, AvatarSource.IDP) }
    }

    @Test
    fun `does not call storeAvatar when no binary avatar is buffered for returning user`() {
        stubLoginSync("returning-user")
        every { memberRepository.findOrganizationIdByUserId("returning-user") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()
        every { userAccountService.consumePendingAvatarUrl("user@example.com") } returns null
        every { userAccountService.consumePendingAvatarBytes("user@example.com") } returns null

        handler.onAuthenticationSuccess(request, response, authToken("returning-user", picture = null))

        verify(exactly = 0) { userAccountService.storeAvatar(any(), any(), any(), any()) }
    }
}
