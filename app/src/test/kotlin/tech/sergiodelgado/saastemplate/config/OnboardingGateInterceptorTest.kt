package tech.sergiodelgado.saastemplate.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import strikt.assertions.isFalse
import tech.sergiodelgado.saasstarter.billing.Subscription
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.util.UUID

class OnboardingGateInterceptorTest {

    private val memberRepository = mockk<MemberRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val interceptor = OnboardingGateInterceptor(memberRepository, subscriptionRepository)

    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val handler = Any()

    private val orgId = UUID.randomUUID()

    @BeforeEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun setAuthenticatedUser(userId: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    @Test
    fun `unauthenticated request passes through`() {
        val result = interceptor.preHandle(request, response, handler)

        expectThat(result).isTrue()
        verify(exactly = 0) { memberRepository.findOrganizationIdByUserId(any()) }
    }

    @Test
    fun `anonymous authentication passes through`() {
        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymous", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))

        val result = interceptor.preHandle(request, response, handler)

        expectThat(result).isTrue()
        verify(exactly = 0) { memberRepository.findOrganizationIdByUserId(any()) }
    }

    @Test
    fun `user with no org is redirected to onboarding organization`() {
        setAuthenticatedUser("new-user")
        every { memberRepository.findOrganizationIdByUserId("new-user") } returns null

        val result = interceptor.preHandle(request, response, handler)

        expectThat(result).isFalse()
        expectThat(response.redirectedUrl).isEqualTo("/onboarding/organization")
    }

    @Test
    fun `user with org but no subscription is redirected to onboarding plan`() {
        setAuthenticatedUser("partial-user")
        every { memberRepository.findOrganizationIdByUserId("partial-user") } returns orgId.toString()
        every { subscriptionRepository.findByOrganizationId(orgId) } returns null

        val result = interceptor.preHandle(request, response, handler)

        expectThat(result).isFalse()
        expectThat(response.redirectedUrl).isEqualTo("/onboarding/plan")
    }

    @Test
    fun `fully onboarded user passes through`() {
        setAuthenticatedUser("full-user")
        every { memberRepository.findOrganizationIdByUserId("full-user") } returns orgId.toString()
        every { subscriptionRepository.findByOrganizationId(orgId) } returns mockk<Subscription>()

        val result = interceptor.preHandle(request, response, handler)

        expectThat(result).isTrue()
    }
}
