package tech.sergiodelgado.saastemplate.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tech.sergiodelgado.saasstarter.security.JwtAuthFilter
import tech.sergiodelgado.saasstarter.security.ZitadelSessionBridgeFilter
import tech.sergiodelgado.saastemplate.auth.ZitadelAuthenticationSuccessHandler

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Autowired(required = false) private val jwtAuthFilter: JwtAuthFilter?,
    @Autowired(required = false) private val testAutoAuthFilter: TestAutoAuthFilter?,
    private val zitadelSessionBridgeFilter: ZitadelSessionBridgeFilter,
    private val oidcLogoutSuccessHandler: OidcClientInitiatedLogoutSuccessHandler,
    private val zitadelSuccessHandler: ZitadelAuthenticationSuccessHandler,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .csrf { it.ignoringRequestMatchers("/webhooks/**") }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/", "/pricing", "/docs/**",
                    "/assets/**", "/css/**", "/js/**",
                    "/actuator/health", "/actuator/prometheus",
                    "/actuator/info", "/actuator/metrics",
                    "/error",
                    "/sign-in", "/sign-up",
                ).permitAll()
                auth.requestMatchers("/webhooks/**").permitAll()
                auth.anyRequest().authenticated()
            }
            .oauth2Login { login ->
                login
                    .loginPage("/sign-in")
                    .successHandler(zitadelSuccessHandler)
            }
            .logout { logout ->
                logout
                    .logoutUrl("/sign-out")
                    .logoutSuccessHandler(oidcLogoutSuccessHandler)
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION", "JSESSIONID")
            }

        jwtAuthFilter?.let { http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java) }
        testAutoAuthFilter?.let { http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java) }
        http.addFilterAfter(zitadelSessionBridgeFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    // Prevent Spring Boot from auto-registering TestAutoAuthFilter as a plain servlet filter.
    // Without this, it runs before Spring Security replaces the SecurityContextHolder, so the
    // authentication it sets gets wiped before the security chain evaluates it.
    @Bean
    @Profile("test")
    fun testAutoAuthFilterRegistration(filter: TestAutoAuthFilter): FilterRegistrationBean<TestAutoAuthFilter> =
        FilterRegistrationBean(filter).also { it.isEnabled = false }
}
