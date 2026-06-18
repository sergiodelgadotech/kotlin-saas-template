package tech.sergiodelgado.saastemplate.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

@Component
class ZitadelAuthenticationSuccessHandler(
    private val memberRepository: MemberRepository,
    private val subscriptionRepository: SubscriptionRepository,
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
        defaultTargetUrl = when {
            orgIdStr == null -> "/onboarding/organization"
            subscriptionRepository.findByOrganizationId(UUID.fromString(orgIdStr)) == null -> "/onboarding/plan"
            else -> "/dashboard"
        }
        super.onAuthenticationSuccess(request, response, authentication)
    }
}
