package tech.sergiodelgado.saastemplate.config

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import tech.sergiodelgado.saastemplate.account.UserAccountService
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.tenant.TenantContext

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
 * **Email** is read directly from the OIDC principal rather than the members table — email
 * changes require a Zitadel re-authentication anyway, so OIDC-claim freshness is sufficient.
 * It is kept here (rather than using `sec:authentication` in the template) so that non-OidcUser
 * principals in tests degrade gracefully to an empty string.
 */
@ControllerAdvice
class NavModelAdvice(
    private val memberRepository: MemberRepository,
    private val userAccountService: UserAccountService,
) {

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

    @ModelAttribute("navAvatarUrl")
    fun navAvatarUrl(@AuthenticationPrincipal principal: OidcUser?): String? {
        if (principal == null) return null
        val sub = principal.subject ?: return null

        // Stored binary image — include a ?v= cache-buster so a new photo isn't masked by a
        // stale cached copy at the fixed /avatar URL. Guard TenantContext.isPresent() because
        // navAvatarUrl runs on every authenticated page, including pre-tenant flows (e.g.
        // onboarding) where the interceptor hasn't set a tenant context yet.
        val stored = if (TenantContext.isPresent()) userAccountService.getStoredAvatar(sub) else null
        if (stored != null) return "/avatar?v=${stored.updatedAt.toEpochMilli()}"

        // Fall back to the external-URL proxy path.
        val avatarUrl = memberRepository.findByExternalUserId(sub)?.avatarUrl
        // Proxy through the app so the browser always loads from the same origin,
        // avoiding tracker-blocking of external avatar domains (e.g. lh3.googleusercontent.com).
        return if (!avatarUrl.isNullOrBlank()) "/avatar" else null
    }
}
