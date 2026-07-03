package tech.sergiodelgado.saastemplate.auth.zitadel

import com.zitadel.Zitadel
import com.zitadel.api.UserServiceApi
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(ZitadelManagementProperties::class)
class ZitadelManagementConfig {

    @Bean
    @ConditionalOnProperty(name = ["saastemplate.zitadel.management.pat"])
    fun userServiceApi(properties: ZitadelManagementProperties): UserServiceApi =
        Zitadel.withAccessToken(properties.baseUrl, properties.pat!!).getUsers()

    @Bean("zitadelManagementRestClient")
    @ConditionalOnProperty(name = ["saastemplate.zitadel.management.pat"])
    fun zitadelManagementRestClient(properties: ZitadelManagementProperties): RestClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader("Authorization", "Bearer ${properties.pat}")
            .build()

    @Bean("gitHubRestClient")
    fun gitHubRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build()

    /**
     * RestClient for Microsoft Graph API.
     * Auth is per-request (token comes from the IDP-intent envelope), so it is not a default header.
     * Short timeouts: photo fetch is best-effort within the 10s Zitadel action timeout.
     */
    @Bean("microsoftGraphRestClient")
    fun microsoftGraphRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("https://graph.microsoft.com/v1.0")
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(2))
                setReadTimeout(Duration.ofSeconds(3))
            })
            .build()
}
