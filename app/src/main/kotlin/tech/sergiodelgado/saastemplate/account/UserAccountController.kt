package tech.sergiodelgado.saastemplate.account

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/account")
class UserAccountController(
    private val userAccountService: UserAccountService,
    @Value("\${AUTH_ISSUER:}") private val authIssuer: String,
) {

    @GetMapping
    fun accountForm(
        @AuthenticationPrincipal oidcUser: OidcUser,
        model: Model,
    ): String {
        val subject = requireNotNull(oidcUser.subject) { "OIDC subject must not be null" }
        val profile = userAccountService.getProfile(subject)
        model.addAttribute("firstName", profile.firstName)
        model.addAttribute("lastName", profile.lastName)
        model.addAttribute("email", oidcUser.email.orEmpty())
        model.addAttribute("idpProfileUrl", "$authIssuer/ui/console/users/me")
        return "account/index"
    }

    @PostMapping
    fun updateAccount(
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @AuthenticationPrincipal oidcUser: OidcUser,
        redirectAttributes: RedirectAttributes,
    ): String {
        val subject = requireNotNull(oidcUser.subject) { "OIDC subject must not be null" }
        userAccountService.updateDisplayName(
            userId = subject,
            givenName = firstName.trim(),
            familyName = lastName.trim(),
            email = oidcUser.email.orEmpty(),
        )
        redirectAttributes.addFlashAttribute("success", "Profile updated")
        return "redirect:/account"
    }
}
