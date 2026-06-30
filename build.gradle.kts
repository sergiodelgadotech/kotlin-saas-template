import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.file.ProjectLayout
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.kotlin.jvm)      apply false
    alias(libs.plugins.kotlin.spring)   apply false
    alias(libs.plugins.spring.boot)     apply false
    alias(libs.plugins.spring.dep)      apply false
}

abstract class ResetTask @Inject constructor(
    private val execOps: ExecOperations,
    private val layout: ProjectLayout,
) : DefaultTask() {

    @TaskAction
    fun run() {
        execOps.exec { commandLine("docker", "compose", "down", "-v") }

        // Podman leaves the zitadel-init container Exited after `down -v`, holding the
        // zitadel-init-keys volume lock. Remove it before trying to drop the volume so the
        // next `up` always gets a fresh key pair (see PR #147).
        val initContainers = ByteArrayOutputStream().use { out ->
            execOps.exec {
                commandLine("docker", "ps", "-a", "-q", "--filter", "name=-zitadel-init-")
                standardOutput = out
            }
            out.toString().trim()
        }
        if (initContainers.isNotEmpty()) {
            execOps.exec {
                commandLine(listOf("docker", "rm") + initContainers.lines().filter { it.isNotBlank() })
                isIgnoreExitValue = true
            }
        }

        val keyVolumes = ByteArrayOutputStream().use { out ->
            execOps.exec {
                commandLine("docker", "volume", "ls", "-q")
                standardOutput = out
            }
            out.toString().lines().map { it.trim() }.filter { it.endsWith("_zitadel-init-keys") }
        }
        if (keyVolumes.isNotEmpty()) {
            execOps.exec {
                commandLine(listOf("docker", "volume", "rm") + keyVolumes)
                isIgnoreExitValue = true
            }
        }

        listOf(
            ".local-client.properties",
            ".local-management.properties",
            "management-api.pat",
            ".smtp-configured",
        ).forEach { layout.projectDirectory.file("docker/zitadel-init/$it").asFile.delete() }

        execOps.exec { commandLine("docker", "compose", "up", "-d", "--remove-orphans", "zitadel-init") }
        execOps.exec { commandLine("docker", "compose", "wait", "zitadel-init") }
        execOps.exec { commandLine("docker", "compose", "down") }
    }
}

tasks.register<ResetTask>("reset") {
    group = "application"
    description = "Resets local dev state: tears down Compose, clears Zitadel volumes/files, re-seeds zitadel-init"
    notCompatibleWithConfigurationCache("runs docker compose imperatively and captures volume listing output")
}
