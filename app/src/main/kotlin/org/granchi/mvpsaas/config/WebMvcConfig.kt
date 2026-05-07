package org.granchi.mvpsaas.config

import org.granchi.saasstarter.ratelimit.RateLimitInterceptor
import org.granchi.saasstarter.tenant.TenantInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val tenantInterceptor: TenantInterceptor,
    private val rateLimitInterceptor: RateLimitInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        // Rate limiting on public/webhook endpoints
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/webhooks/**")

        // Tenant resolution on authenticated app routes
        registry.addInterceptor(tenantInterceptor)
            .addPathPatterns(
                "/app/**",
                "/dashboard/**",
                "/billing/**",
                "/organization/**"
            )
            .excludePathPatterns(
                "/webhooks/**",
                "/",
                "/pricing",
                "/docs/**"
            )
    }
}
