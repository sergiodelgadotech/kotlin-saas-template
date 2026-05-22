package org.granchi.saastemplate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

// Scan starter beans (e.g. JwtAuthFilter) until plans 2-7 move them behind
// dedicated @AutoConfiguration classes in kotlin-saas-starter.
//
// @EnableJdbcRepositories is explicit here because the starter's
// OrganizationAutoConfiguration already registers a JdbcRepositoryConfigExtension
// for its own package, which causes Spring Boot's JdbcRepositoriesAutoConfiguration
// to skip its default scan. We must therefore explicitly declare scanning for the
// template's own repositories.
@SpringBootApplication
@ComponentScan(basePackages = ["org.granchi.saastemplate", "org.granchi.saasstarter"])
@EnableJdbcRepositories(basePackages = ["org.granchi.saastemplate"])
class SaasTemplateApplication

fun main(args: Array<String>) {
    runApplication<SaasTemplateApplication>(*args)
}
