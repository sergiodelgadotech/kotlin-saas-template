rootProject.name = "mvp-saas-template"
include("app", "web", "infra")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // kotlin-saas-starter is published to GitHub Packages
        maven {
            url = uri("https://maven.pkg.github.com/granchi/kotlin-saas-starter")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
