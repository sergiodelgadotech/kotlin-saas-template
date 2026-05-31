package tech.sergiodelgado.saastemplate.config

import tech.sergiodelgado.saasstarter.security.JwtAuthFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers(
                "/", "/pricing", "/docs/**",
                "/assets/**", "/css/**", "/js/**",
                "/actuator/health", "/actuator/prometheus",
                "/actuator/info", "/actuator/metrics", "/actuator/metrics/**"
            ).permitAll()
            auth.requestMatchers("/webhooks/**").permitAll()
            auth.anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()
}
