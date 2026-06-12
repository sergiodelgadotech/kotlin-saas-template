package tech.sergiodelgado.saastemplate.config

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import tech.sergiodelgado.saasstarter.organization.MemberRepository

/**
 * Injects nav display attributes (initials, display name, email) into every
 * authenticated controller's model.
 *
 * **Why read from the members table instead of OIDC claims?**
 * The OIDC id_token is issued at login time and stays in the session until the
 * user signs out. If the user edits their name on /account, the id_token still
 * carries the old placeholder (e.g. "Bob Bob"). Reading from the members table —
 * which UserAccountService updates immediately on save — means the nav reflects
 * the new name on the very next page load, without requiring a sign-out/sign-in.
 *
 * **Why not use the Thymeleaf Security extras dialect (#authentication / sec:)?**
 * The dialect isn't on the classpath yet. When it is added (to enable sec:authorize
 * for role-based nav sections), this advice can be retired for the display-name
 * concern but should remain for the members-table read so initials stay live.
 * See the GitHub issue tracking the Thymeleaf Security extras addition.
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
