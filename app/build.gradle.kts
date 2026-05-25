import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dep)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    // ── kotlin-saas-starter (transversal infrastructure) ──────────────────────
    implementation(libs.kotlin.saas.starter)

    // ── Spring Boot ──────────────────────────────────────────────────────────
    implementation(libs.spring.boot.web)
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
    // stripe is inherited transitively from kotlin-saas-starter (api dependency)
    implementation(libs.resend)
    implementation(libs.sentry)

    // ── Validation ───────────────────────────────────────────────────────────
    implementation(libs.konform)

    // ── Data analysis ────────────────────────────────────────────────────────
    implementation(libs.smile.core)
    implementation(libs.smile.kotlin)
    implementation(libs.kotlin.dataframe)

    // ── Tests ────────────────────────────────────────────────────────────────
    testImplementation(libs.spring.boot.test)
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
// Force ASM 9.8: Spring Boot 3.5.x manages ASM to 9.7.1, but JobRunr uses ASM to parse
// job-lambda bytecode at runtime. With jvmToolchain(25) the compiled classes are Java 25
// (major version 69), which ASM 9.7.1 cannot read. ASM 9.8 adds support for Java 25.
// Spring Framework uses its own shaded ASM inside spring-core and is unaffected by this.
configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        force(libs.asm)
    }
}

tasks.register<Test>("unitTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { excludeTags("integration", "e2e", "architecture") }
}
tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}
tasks.register<Test>("e2eTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("e2e") }
}
tasks.register<Test>("architectureTest") {
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("architecture") }
}
tasks.test { useJUnitPlatform { excludeTags("integration", "e2e", "architecture") } }
