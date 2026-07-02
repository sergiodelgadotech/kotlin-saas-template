package tech.sergiodelgado.saastemplate.dashboard

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class HomeControllerTest {

    private val controller = HomeController()

    @Test
    fun `root redirects to dashboard when authenticated`() {
        val principal = mockk<OidcUser>()
        expectThat(controller.root(principal)).isEqualTo("redirect:/dashboard")
    }

    @Test
    fun `root redirects to sign-in when not authenticated`() {
        expectThat(controller.root(null)).isEqualTo("redirect:/sign-in")
    }
}
