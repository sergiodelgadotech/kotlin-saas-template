package tech.sergiodelgado.saastemplate.config

import tech.sergiodelgado.saasstarter.security.JwtAuthFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
                    "/actuator/info", "/actuator/metrics"
                ).permitAll()
                auth.requestMatchers("/webhooks/**").permitAll()
                auth.anyRequest().authenticated()
            }
        jwtAuthFilter?.let { http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java) }
        localDevAuthFilter?.let { http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java) }
        return http.build()
    }
}
