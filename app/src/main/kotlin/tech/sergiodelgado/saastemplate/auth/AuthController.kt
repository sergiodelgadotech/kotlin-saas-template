package tech.sergiodelgado.saastemplate.auth

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AuthController {

    @GetMapping("/sign-in")
    fun signIn() = "redirect:/oauth2/authorization/zitadel"

    @GetMapping("/sign-up")
    fun signUp() = "redirect:/oauth2/authorization/zitadel"
}
