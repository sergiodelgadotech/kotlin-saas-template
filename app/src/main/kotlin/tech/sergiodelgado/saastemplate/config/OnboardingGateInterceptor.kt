package tech.sergiodelgado.saastemplate.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

class OnboardingGateInterceptor(
    private val memberRepository: MemberRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val userId = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated && it !is AnonymousAuthenticationToken }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: return true

        val orgIdStr = memberRepository.findOrganizationIdByUserId(userId)
        if (orgIdStr == null) {
            response.sendRedirect(request.contextPath + "/onboarding/organization")
            return false
        }

        if (subscriptionRepository.findByOrganizationId(UUID.fromString(orgIdStr)) == null) {
            response.sendRedirect(request.contextPath + "/onboarding/plan")
            return false
        }

        return true
    }
}
