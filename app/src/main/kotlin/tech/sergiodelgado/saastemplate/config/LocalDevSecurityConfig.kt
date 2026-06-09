package tech.sergiodelgado.saastemplate.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler

/**
 * Local-profile security beans that keep the application context healthy without a running Zitadel.
 *
 * Spring Boot 4 / Spring Security 7's [org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper]
 * calls [org.springframework.security.oauth2.client.registration.ClientRegistrations.fromIssuerLocation]
 * for every registration that has an `issuer-uri` property. When `AUTH_ISSUER` is not set (or
 * resolves to an empty/malformed value), this throws at context startup before any auth logic runs.
 *
 * Providing beans here satisfies the `@ConditionalOnMissingBean` guards in Spring Boot's
 * OAuth2 autoconfiguration, so the property-mapper path is skipped entirely in the local profile.
 * [LocalDevAuthFilter] still handles all authentication — these beans are wired into [SecurityConfig]
 * but never exercised in practice during local development.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalDevSecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("zitadel")
                .clientId("local-dev-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/zitadel")
                .scope("openid", "profile", "email")
                .authorizationUri("http://localhost:8089/oauth/v2/authorize")
                .tokenUri("http://localhost:8089/oauth/v2/token")
                .jwkSetUri("http://localhost:8089/oauth/v2/keys")
                .userInfoUri("http://localhost:8089/oidc/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Zitadel")
                .build()
        )

    /** Sign-out redirects to "/" locally without contacting Zitadel's end-session endpoint. */
    @Bean
    @ConditionalOnMissingBean(OidcClientInitiatedLogoutSuccessHandler::class)
    fun oidcLogoutSuccessHandler(
        clientRegistrationRepository: ClientRegistrationRepository,
    ): OidcClientInitiatedLogoutSuccessHandler {
        val delegate = SimpleUrlLogoutSuccessHandler().apply { setDefaultTargetUrl("/") }
        return object : OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository) {
            override fun onLogoutSuccess(
                request: HttpServletRequest,
                response: HttpServletResponse,
                authentication: Authentication?,
            ) = delegate.onLogoutSuccess(request, response, authentication)
        }
    }
}
