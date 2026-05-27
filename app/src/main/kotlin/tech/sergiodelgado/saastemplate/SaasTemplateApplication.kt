package tech.sergiodelgado.saastemplate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

// @EnableJdbcRepositories is explicit here because the starter's
// OrganizationAutoConfiguration already registers a JdbcRepositoryConfigExtension
// for its own package, which causes Spring Boot's JdbcRepositoriesAutoConfiguration
// to skip its default scan. We must therefore explicitly declare scanning for the
// template's own repositories.
@SpringBootApplication
@ComponentScan(basePackages = ["tech.sergiodelgado.saastemplate"])
@EnableJdbcRepositories(basePackages = ["tech.sergiodelgado.saastemplate"])
class SaasTemplateApplication

fun main(args: Array<String>) {
    runApplication<SaasTemplateApplication>(*args)
}
