package tech.sergiodelgado.saastemplate.config

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import tech.sergiodelgado.saasstarter.organization.MemberRepository

/**
 * Injects nav display attributes into every authenticated controller's model so
 * the nav fragment doesn't need the Thymeleaf Security extras dialect.
 * Reads from the local members table (source of truth after profile self-edits).
 */
@ControllerAdvice
class NavModelAdvice(private val memberRepository: MemberRepository) {

    @ModelAttribute("navInitials")
    fun navInitials(@AuthenticationPrincipal principal: OidcUser?): String {
        if (principal == null) return "?"
        val sub = principal.subject ?: return "?"
        val member = memberRepository.findByExternalUserId(sub)
        val first = member?.firstName?.firstOrNull()?.uppercaseChar()
        val last = member?.lastName?.firstOrNull()?.uppercaseChar()
        return when {
            first != null && last != null -> "$first$last"
            first != null -> "$first"
            else -> principal.email?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        }
    }

    @ModelAttribute("navDisplayName")
    fun navDisplayName(@AuthenticationPrincipal principal: OidcUser?): String {
        if (principal == null) return ""
        val sub = principal.subject ?: return ""
        val member = memberRepository.findByExternalUserId(sub)
        val first = member?.firstName.orEmpty()
        val last = member?.lastName.orEmpty()
        return if (first.isNotBlank() || last.isNotBlank()) "$first $last".trim() else principal.email.orEmpty()
    }

    @ModelAttribute("navEmail")
    fun navEmail(@AuthenticationPrincipal principal: OidcUser?): String =
        principal?.email.orEmpty()
}
