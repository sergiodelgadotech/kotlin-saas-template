package tech.sergiodelgado.saastemplate.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import tech.sergiodelgado.saastemplate.account.UserAccountService
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

@Component
class ZitadelAuthenticationSuccessHandler(
    private val memberRepository: MemberRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userAccountService: UserAccountService,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oidcUser = (authentication as OAuth2AuthenticationToken).principal as OidcUser
        val subject = requireNotNull(oidcUser.subject) { "OIDC subject must not be null" }
        // Keep profile columns fresh from the IdP's own source of truth.
        // No-op if the member row doesn't exist yet (first-time user, no org created).
        memberRepository.updateProfile(subject, oidcUser.email.orEmpty(), oidcUser.givenName, oidcUser.familyName)
        val orgIdStr = memberRepository.findOrganizationIdByUserId(subject)
        val email = oidcUser.email
        if (orgIdStr != null) {
            // Returning user: consume webhook buffer, fall back to OIDC picture claim.
            // Google sets picture via the webhook buffer (Zitadel doesn't forward Google's
            // picture in its id_token). GitHub and others set it via Zitadel's own picture
            // claim, so the oidcUser.picture fallback handles those.
            val pendingAvatar = email?.let { userAccountService.consumePendingAvatarUrl(it) }
            val pictureToSet = pendingAvatar ?: oidcUser.picture
            if (pictureToSet != null) {
                memberRepository.updateAvatarUrl(subject, pictureToSet)
            }
        } else if (email != null && oidcUser.picture != null) {
            // New user: OIDC picture claim available (e.g. GitHub/Slack forwarded via Zitadel).
            // Buffer it so OnboardingService.createOrganization can consume it — the webhook
            // buffer may not have it if the IDP's userName is a handle rather than an email.
            userAccountService.syncAvatarFromIdp(email, oidcUser.picture!!)
        }
        defaultTargetUrl = when {
            orgIdStr == null -> "/onboarding/organization"
            subscriptionRepository.findByOrganizationId(UUID.fromString(orgIdStr)) == null -> "/onboarding/plan"
            else -> "/dashboard"
        }
        super.onAuthenticationSuccess(request, response, authentication)
    }
}
