package tech.sergiodelgado.saastemplate.account

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository

class AvatarControllerTest {

    private val memberRepository = mockk<MemberRepository>()
    private val controller = AvatarController(memberRepository)
    private val principal = mockk<OidcUser> { every { subject } returns "user-123" }

    private fun member(avatarUrl: String?) = mockk<Member> { every { this@mockk.avatarUrl } returns avatarUrl }

    @Test
    fun `returns 404 when member has no avatar`() {
        every { memberRepository.findByExternalUserId("user-123") } returns member(null)
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }

    @Test
    fun `returns 404 when member has blank avatar url`() {
        every { memberRepository.findByExternalUserId("user-123") } returns member("")
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }

    @Test
    fun `returns 404 when member not found`() {
        every { memberRepository.findByExternalUserId("user-123") } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }
}
