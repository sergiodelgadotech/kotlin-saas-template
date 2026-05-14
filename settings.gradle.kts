pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "kotlin-saas-template"
include("app", "web", "infra")

val starterPath = file("../kotlin-saas-starter")
if (starterPath.exists()) {
    includeBuild(starterPath)
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // kotlin-saas-starter is published to GitHub Packages
        maven {
            url = uri("https://maven.pkg.github.com/sergiodelgadotech/kotlin-saas-starter")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
