package org.granchi.mvpsaas

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MvpSaasApplication

fun main(args: Array<String>) {
    runApplication<MvpSaasApplication>(*args)
}
