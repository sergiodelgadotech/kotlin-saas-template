package tech.sergiodelgado.saastemplate.config

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import tech.sergiodelgado.saasstarter.organization.MemberRepository

/**
 * Injects nav display attributes (initials, display name) into every
 * authenticated controller's model.
 *
 * **Why read from the members table instead of OIDC claims?**
 * The OIDC id_token is issued at login time and stays in the session until the
 * user signs out. If the user edits their name on /account, the id_token still
 * carries the old placeholder (e.g. "Bob Bob"). Reading from the members table —
 * which UserAccountService updates immediately on save — means the nav reflects
 * the new name on the very next page load, without requiring a sign-out/sign-in.
 *
 * **Email** is rendered via `sec:authentication="principal.email"` in the template
 * and is intentionally not injected here — email changes require a Zitadel
 * re-authentication anyway, so OIDC-claim freshness is sufficient.
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
}
