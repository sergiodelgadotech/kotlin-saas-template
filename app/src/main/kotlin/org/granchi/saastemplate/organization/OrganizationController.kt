package org.granchi.saastemplate.organization

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/organization")
class OrganizationController(private val organizationService: OrganizationService) {

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
}
