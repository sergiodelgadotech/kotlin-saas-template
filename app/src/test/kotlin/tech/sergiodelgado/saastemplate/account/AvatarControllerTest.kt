package tech.sergiodelgado.saastemplate.account

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class AvatarControllerTest {

    private val userAccountService = mockk<UserAccountService>()
    private val controller = AvatarController(userAccountService)
    private val principal = mockk<OidcUser> { every { subject } returns "user-123" }

    @Test
    fun `returns 404 when member has no avatar`() {
        every { userAccountService.getAvatarUrl("user-123") } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }

    @Test
    fun `returns 404 when subject is null`() {
        every { principal.subject } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }
}
