package tech.sergiodelgado.saastemplate.dashboard

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {

    @GetMapping("/")
    fun root(@AuthenticationPrincipal principal: OidcUser?) =
        if (principal != null) "redirect:/dashboard" else "redirect:/sign-in"
}
