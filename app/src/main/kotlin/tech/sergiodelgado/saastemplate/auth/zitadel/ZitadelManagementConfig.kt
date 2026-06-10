package tech.sergiodelgado.saastemplate.auth.zitadel

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ZitadelManagementProperties::class)
class ZitadelManagementConfig
