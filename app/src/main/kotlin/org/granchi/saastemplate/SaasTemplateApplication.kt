package org.granchi.saastemplate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SaasTemplateApplication

fun main(args: Array<String>) {
    runApplication<SaasTemplateApplication>(*args)
}
