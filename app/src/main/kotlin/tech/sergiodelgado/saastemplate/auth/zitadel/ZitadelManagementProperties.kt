package tech.sergiodelgado.saastemplate.auth.zitadel

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("saastemplate.zitadel.management")
data class ZitadelManagementProperties(
    val baseUrl: String = "",
    val pat: String = "",
    val organizationId: String = ""
)
