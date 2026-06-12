package tech.sergiodelgado.saastemplate.auth.zitadel

import com.zitadel.Zitadel
import com.zitadel.api.UserServiceApi
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ZitadelManagementProperties::class)
class ZitadelManagementConfig {

    @Bean
    @ConditionalOnProperty(name = ["saastemplate.zitadel.management.pat"])
    fun userServiceApi(properties: ZitadelManagementProperties): UserServiceApi =
        Zitadel.withAccessToken(properties.baseUrl, properties.pat!!).getUsers()
}
