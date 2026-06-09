package tech.sergiodelgado.saastemplate.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
 * Test-profile security beans that keep the application context healthy without a running Zitadel.
 *
 * ## Why two beans?
 *
 * ### [clientRegistrationRepository]
 * Spring Boot 3.4 / Spring Security 7 changed [OAuth2ClientPropertiesMapper]: it now calls
 * [org.springframework.security.oauth2.client.registration.ClientRegistrations.fromIssuerLocation]
 * even when `issuer-uri` is absent, throwing `issuer cannot be empty` before falling back to
 * `authorization-uri`. Providing our own [ClientRegistrationRepository] bean satisfies the
 * `@ConditionalOnMissingBean(ClientRegistrationRepository::class)` guard in
 * `OAuth2ClientConfigurations$ClientRegistrationRepositoryConfiguration` so the autoconfiguration
 * step is skipped entirely.
 *
 * ### [oidcLogoutSuccessHandler]
 * The production handler requires a resolved OIDC end-session endpoint (i.e. `issuer-uri`).
 * Overriding it with a [SimpleUrlLogoutSuccessHandler] delegate avoids any issuer-dependent logic.
 * [ZitadelOidcAutoConfiguration] is `@ConditionalOnMissingBean(OidcClientInitiatedLogoutSuccessHandler::class)`,
 * so this bean also prevents the auto-configured handler from being created.
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestSecurityConfig {

    /**
     * Provides a [ClientRegistrationRepository] built directly from the test provider URIs,
     * bypassing Spring Boot's [OAuth2ClientPropertiesMapper] which now requires `issuer-uri`.
     *
     * The registration mirrors the values in `application-test.yml` so that
     * `oidcLogin()` post-processors in MockMvc tests can resolve the registration by ID.
     */
    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val registration = ClientRegistration
            .withRegistrationId("zitadel")
            .clientId("test-client-id")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
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
        return InMemoryClientRegistrationRepository(registration)
    }

    /**
     * Replaces [OidcClientInitiatedLogoutSuccessHandler] so sign-out redirects to "/" without
     * requiring a live Zitadel end-session endpoint.
     */
    @Bean
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
