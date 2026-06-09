package tech.sergiodelgado.saastemplate.auth

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import tech.sergiodelgado.saasstarter.organization.MemberRepository

class ZitadelAuthenticationSuccessHandlerTest {

    private val memberRepository = mockk<MemberRepository>()
    private val handler = ZitadelAuthenticationSuccessHandler(memberRepository)
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()

    private fun authToken(subject: String): OAuth2AuthenticationToken {
        val oidcUser = mockk<OidcUser> { every { this@mockk.subject } returns subject }
        return OAuth2AuthenticationToken(oidcUser, emptyList(), "zitadel")
    }

    @Test
    fun `redirects to dashboard when member exists`() {
        every { memberRepository.findOrganizationIdByUserId("user-abc") } returns "org-uuid-string"

        handler.onAuthenticationSuccess(request, response, authToken("user-abc"))

        expectThat(response.redirectedUrl).isEqualTo("/dashboard")
    }

    @Test
    fun `redirects to organization new when member does not exist`() {
        every { memberRepository.findOrganizationIdByUserId("new-user") } returns null

        handler.onAuthenticationSuccess(request, response, authToken("new-user"))

        expectThat(response.redirectedUrl).isEqualTo("/organization/new")
    }
}
