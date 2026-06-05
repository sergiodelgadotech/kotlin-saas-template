package tech.sergiodelgado.saastemplate.config

import tech.sergiodelgado.saasstarter.security.JwtAuthFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Autowired(required = false) private val jwtAuthFilter: JwtAuthFilter?,
    @Autowired(required = false) private val localDevAuthFilter: LocalDevAuthFilter?
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/", "/pricing", "/docs/**",
                    "/assets/**", "/css/**", "/js/**",
                    "/actuator/health", "/actuator/prometheus",
                    "/actuator/info", "/actuator/metrics",
                    "/error"
                ).permitAll()
                auth.requestMatchers("/webhooks/**").permitAll()
                auth.anyRequest().authenticated()
            }
            .logout {
                it.logoutUrl("/sign-out")
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION", "JSESSIONID")
            }
        jwtAuthFilter?.let { http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java) }
        localDevAuthFilter?.let { http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java) }
        return http.build()
    }

    // Prevent Spring Boot from auto-registering LocalDevAuthFilter as a plain servlet filter.
    // Without this, it runs before Spring Security replaces the SecurityContextHolder, so the
    // authentication it sets gets wiped before the security chain evaluates it.
    @Bean
    @Profile("local")
    fun localDevAuthFilterRegistration(filter: LocalDevAuthFilter): FilterRegistrationBean<LocalDevAuthFilter> =
        FilterRegistrationBean(filter).also { it.isEnabled = false }
}
