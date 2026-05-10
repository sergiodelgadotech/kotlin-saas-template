package org.granchi.saastemplate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

// Scan starter beans (e.g. JwtAuthFilter) until plans 2-7 move them behind
// dedicated @AutoConfiguration classes in kotlin-saas-starter.
@SpringBootApplication
@ComponentScan(basePackages = ["org.granchi.saastemplate", "org.granchi.saasstarter"])
class SaasTemplateApplication

fun main(args: Array<String>) {
    runApplication<SaasTemplateApplication>(*args)
}
