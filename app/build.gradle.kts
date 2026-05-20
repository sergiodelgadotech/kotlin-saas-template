plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dep)
    alias(libs.plugins.mokkery)
}

kotlin {
    jvmToolchain(21)
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
    implementation(libs.stripe)
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
}

// kotlin-saas-starter is pinned to main-SNAPSHOT during active development.
// Tell Gradle never to cache it so CI always resolves the latest published snapshot.
// Locally the composite build substitutes the sibling source, so this has no effect there.
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, java.util.concurrent.TimeUnit.SECONDS)
}

tasks.register<Test>("unitTest")           { useJUnitPlatform { excludeTags("integration", "e2e", "architecture") } }
tasks.register<Test>("integrationTest")    { useJUnitPlatform { includeTags("integration") } }
tasks.register<Test>("e2eTest")            { useJUnitPlatform { includeTags("e2e") } }
tasks.register<Test>("architectureTest")   { useJUnitPlatform { includeTags("architecture") } }
tasks.test                                 { useJUnitPlatform { excludeTags("integration", "e2e") } }
