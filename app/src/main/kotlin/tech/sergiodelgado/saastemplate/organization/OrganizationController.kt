package tech.sergiodelgado.saastemplate.organization

import tech.sergiodelgado.saasstarter.organization.OrganizationService
import tech.sergiodelgado.saasstarter.validation.DomainValidationException
import tech.sergiodelgado.saasstarter.web.ForbiddenException
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/organization")
class OrganizationController(
    private val organizationService: OrganizationService,
    private val onboardingService: OnboardingService,
    private val memberInvitationService: MemberInvitationService,
) {

    @GetMapping("/new")
    fun newOrganization(): String = "organization/new"

    @PostMapping("/new")
    fun createOrganization(
        @RequestParam name: String,
        @AuthenticationPrincipal oidcUser: OidcUser,
    ): String {
        onboardingService.createOrganization(
            oidcUser.subject, name,
            email = oidcUser.email.orEmpty(),
            firstName = oidcUser.givenName,
            lastName = oidcUser.familyName,
        )
        return "redirect:/dashboard"
    }

    @GetMapping("/settings")
    fun settings(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        return "organization/settings"
    }

    @PostMapping("/settings")
    fun updateSettings(
        @RequestParam name: String,
        redirectAttributes: RedirectAttributes
    ): String {
        organizationService.updateName(name)
        redirectAttributes.addFlashAttribute("success", "Settings updated successfully")
        return "redirect:/organization/settings"
    }

    @GetMapping("/members")
    fun members(model: Model): String {
        model.addAttribute("organization", organizationService.current())
        model.addAttribute("members", organizationService.members())
        return "organization/members"
    }

    @GetMapping("/members/invite")
    fun inviteForm(): String = "organization/invite"

    @PostMapping("/members/invite")
    fun invite(
        @RequestParam email: String,
        @RequestParam role: String,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String {
        return try {
            memberInvitationService.invite(email, role)
            redirectAttributes.addFlashAttribute("success", "Invitation sent to $email")
            "redirect:/organization/members"
        } catch (ex: ForbiddenException) {
            redirectAttributes.addFlashAttribute("error", "Not authorized to invite members")
            "redirect:/organization/members"
        } catch (ex: DomainValidationException) {
            model.addAttribute("email", email)
            model.addAttribute("role", role)
            model.addAttribute("errors", ex.errors.map { "${it.dataPath}: ${it.message}" })
            "organization/invite"
        } catch (ex: IllegalStateException) {
            model.addAttribute("email", email)
            model.addAttribute("role", role)
            model.addAttribute("configError", ex.message)
            "organization/invite"
        }
    }
}
