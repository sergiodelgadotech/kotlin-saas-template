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
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

class ZitadelAuthenticationSuccessHandlerTest {

    private val memberRepository = mockk<MemberRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val handler = ZitadelAuthenticationSuccessHandler(memberRepository, subscriptionRepository)
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
    }

    @Test
    fun `redirects to dashboard when member and subscription exist`() {
        stubLoginSync("user-abc")
        every { memberRepository.findOrganizationIdByUserId("user-abc") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()

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

        handler.onAuthenticationSuccess(request, response, authToken("partial-user"))

        expectThat(response.redirectedUrl).isEqualTo("/onboarding/plan")
    }

    @Test
    fun `updates member profile from OIDC claims on login`() {
        stubLoginSync("user-xyz")
        every { memberRepository.findOrganizationIdByUserId("user-xyz") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()

        handler.onAuthenticationSuccess(request, response, authToken("user-xyz", "jane@example.com", "Jane", "Doe"))

        verify { memberRepository.updateProfile("user-xyz", "jane@example.com", "Jane", "Doe") }
    }

    @Test
    fun `syncs OIDC picture claim to avatar on login`() {
        stubLoginSync("user-pic")
        every { memberRepository.findOrganizationIdByUserId("user-pic") } returns orgIdStr
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()

        handler.onAuthenticationSuccess(
            request, response,
            authToken("user-pic", picture = "https://example.com/avatar.jpg"),
        )

        verify { memberRepository.updateAvatarUrl("user-pic", "https://example.com/avatar.jpg") }
    }

    @Test
    fun `syncs null picture when OIDC has no picture claim`() {
        stubLoginSync("user-no-pic")
        every { memberRepository.findOrganizationIdByUserId("user-no-pic") } returns null

        handler.onAuthenticationSuccess(request, response, authToken("user-no-pic", picture = null))

        verify { memberRepository.updateAvatarUrl("user-no-pic", null) }
    }
}
