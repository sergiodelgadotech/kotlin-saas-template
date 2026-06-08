package tech.sergiodelgado.saastemplate.dashboard

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class HomeControllerTest {

    private val controller = HomeController()

    @Test
    fun `root redirects to dashboard`() {
        expectThat(controller.root()).isEqualTo("redirect:/dashboard")
    }
}
