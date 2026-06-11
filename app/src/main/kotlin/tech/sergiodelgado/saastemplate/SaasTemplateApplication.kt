package tech.sergiodelgado.saastemplate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.scheduling.annotation.EnableScheduling

// @EnableJdbcRepositories is explicit here because the starter's
// OrganizationAutoConfiguration already registers a JdbcRepositoryConfigExtension
// for its own package, which causes Spring Boot's JdbcRepositoriesAutoConfiguration
// to skip its default scan. We must therefore explicitly declare scanning for the
// template's own repositories.
// TypeExcludeFilter mirrors the exclusion already on @SpringBootApplication's built-in
// @ComponentScan, ensuring @TestConfiguration classes are excluded from this second scan too.
@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = ["tech.sergiodelgado.saastemplate"],
    excludeFilters = [ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class])],
)
@EnableJdbcRepositories(basePackages = ["tech.sergiodelgado.saastemplate"])
class SaasTemplateApplication

fun main(args: Array<String>) {
    runApplication<SaasTemplateApplication>(*args)
}
