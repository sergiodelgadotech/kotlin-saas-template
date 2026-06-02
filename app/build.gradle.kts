import java.util.concurrent.TimeUnit
import com.github.jk1.license.render.JsonReportRenderer
import com.github.gradle.node.npm.task.NpmTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dep)
    alias(libs.plugins.dependency.license.report)
    alias(libs.plugins.kover)
    alias(libs.plugins.node.gradle)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

licenseReport {
    configurations = arrayOf("runtimeClasspath")
    renderers = arrayOf(JsonReportRenderer("licenses.json"))
}

dependencies {
    // ── kotlin-saas-starter (transversal infrastructure) ──────────────────────
    implementation(libs.kotlin.saas.starter)

    // ── Spring Boot ──────────────────────────────────────────────────────────
    implementation(libs.spring.boot.web) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation(libs.spring.boot.jetty)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.thymeleaf)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.data.jdbc)
    implementation(libs.spring.boot.redis)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.session.redis)
    implementation(libs.thymeleaf.layout)

    // ── Database ─────────────────────────────────────────────────────────────
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    implementation(libs.postgresql)

    // ── Async jobs ───────────────────────────────────────────────────────────
    implementation(libs.jobrunr.spring)

    // ── External services ────────────────────────────────────────────────────
    // stripe + resend are inherited transitively from kotlin-saas-starter (api dependencies)
    implementation(libs.sentry)

    // ── Observability ─────────────────────────────────────────────────────────
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.logstash.logback.encoder)

    // ── Validation ───────────────────────────────────────────────────────────
    implementation(libs.konform)

    // ── Data analysis ────────────────────────────────────────────────────────
    implementation(libs.smile.core)
    implementation(libs.smile.kotlin)
    implementation(libs.kotlin.dataframe)

    // ── Tests ────────────────────────────────────────────────────────────────
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.flyway)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.playwright)
    testImplementation(libs.konsist)
    testImplementation(libs.strikt.core)
    testImplementation(libs.mockk)

    // Gradle 9's useJUnitPlatform() no longer auto-adds the launcher.
    testRuntimeOnly(libs.junit.platform.launcher)
}

// kotlin-saas-starter is pinned to main-SNAPSHOT during active development.
// Tell Gradle never to cache it so CI always resolves the latest published snapshot.
// Locally the composite build substitutes the sibling source, so this has no effect there.
//
// Force ASM 9.10.1: JobRunr uses ASM to parse job-lambda bytecode at runtime.
// With jvmToolchain(25) the compiled classes are Java 25 (major version 69),
// which BOM-managed ASM versions may not support. 9.10.1 covers Java 25.
// Spring Framework uses its own shaded ASM inside spring-core and is unaffected.
configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        force(libs.asm)
    }
}

node {
    version = "22.16.0"
    download = true
}

tasks.register<NpmTask>("buildCss") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "build"))
    inputs.files(
        fileTree("src/css"),
        fileTree("src/main/resources/templates")
    )
    outputs.dir("src/main/resources/static")
}

tasks.processResources {
    dependsOn("buildCss")
}

tasks.clean {
    delete(file("src/main/resources/static"))
}

tasks.register<Test>("unitTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { excludeTags("integration", "e2e", "architecture") }
}
val integrationTest = tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}
val e2eTest = tasks.register<Test>("e2eTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("e2e") }
}
val architectureTest = tasks.register<Test>("architectureTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("architecture") }
}
tasks.test { useJUnitPlatform { excludeTags("integration", "e2e", "architecture") } }
tasks.check { dependsOn(integrationTest, e2eTest, architectureTest, tasks.named("koverVerify")) }

tasks.register<JavaExec>("playwrightInstall") {
    description = "Downloads Playwright browser binaries (Chromium) and system deps via the bundled CLI"
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps", "chromium")
}

// ── NOTICE generation ─────────────────────────────────────────────────────────
tasks.register("generateNotice") {
    dependsOn("generateLicenseReport")

    val reportFile = layout.buildDirectory.file("reports/dependency-license/licenses.json")
    inputs.file(reportFile)
    outputs.file(rootProject.file("NOTICE"))

    doLast {
        @Suppress("UNCHECKED_CAST")
        val json = groovy.json.JsonSlurper().parse(reportFile.get().asFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val deps = (json["dependencies"] as? List<Map<String, Any?>>).orEmpty()

        val content = buildString {
            appendLine("kotlin-saas-template")
            appendLine("Copyright (c) 2026 Sergio Delgado")
            appendLine()
            appendLine("This product includes the following third-party components:")
            deps.sortedBy { it["moduleName"] as? String ?: "" }.forEach { dep ->
                val name = dep["moduleName"] as? String ?: run {
                    logger.warn("generateNotice: skipping dependency with no moduleName: $dep")
                    return@forEach
                }
                val version = dep["moduleVersion"] as? String ?: ""
                val license = dep["moduleLicense"] as? String ?: "Unknown"
                val url     = dep["moduleLicenseUrl"] as? String
                appendLine()
                appendLine("------------------------------------------------------------------------")
                appendLine("$name:$version")
                appendLine("License: $license")
                if (url != null) appendLine(url)
            }
            appendLine("------------------------------------------------------------------------")
        }

        rootProject.file("NOTICE").writeText(content)
        logger.lifecycle("NOTICE written to ${rootProject.file("NOTICE").absolutePath}")
    }
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(80)
                }
            }
        }
    }
}

tasks.named("build") {
    dependsOn("generateNotice")
}
