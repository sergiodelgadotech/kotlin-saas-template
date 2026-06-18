package tech.sergiodelgado.saastemplate.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tech.sergiodelgado.saasstarter.billing.SubscriptionRepository
import tech.sergiodelgado.saasstarter.organization.MemberRepository

@Configuration
class WebMvcConfig(
    private val memberRepository: MemberRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(OnboardingGateInterceptor(memberRepository, subscriptionRepository))
            .addPathPatterns("/app/**", "/dashboard/**", "/billing/**", "/organization/**")
    }
}
