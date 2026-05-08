package org.granchi.mvpsaas.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withoutName
import com.lemonappdev.konsist.api.ext.list.withoutPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("architecture")
class ArchitectureTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `controllers do not access repositories directly`() {
        scope.classes()
            .withNameEndingWith("Controller")
            .assertFalse {
                it.containingFile.imports.any { imp -> imp.name.endsWith("Repository") }
            }
    }

    @Test
    fun `services do not have HTTP dependencies`() {
        scope.classes()
            .withNameEndingWith("Service")
            .withoutPackage("..core..")
            .assertFalse {
                it.containingFile.imports.any { imp ->
                    imp.name.startsWith("jakarta.servlet") ||
                        imp.name.startsWith("org.springframework.web.bind.annotation")
                }
            }
    }

    @Test
    fun `tenant context is not used in controllers`() {
        scope.classes()
            .withNameEndingWith("Controller")
            .assertFalse {
                it.containingFile.imports.any { imp -> imp.name.contains("TenantContext") }
            }
    }

    @Test
    fun `webhook controllers do not use tenant context`() {
        scope.classes()
            .withNameEndingWith("WebhookController")
            .assertFalse {
                it.containingFile.imports.any { imp -> imp.name.contains("TenantContext") }
            }
    }

    @Test
    fun `domain entities have organizationId field`() {
        scope.classes()
            .withAnnotationNamed("Table")
            .withoutPackage("..core..")
            .withoutName("Organization")
            .assertTrue {
                it.properties().any { prop -> prop.name == "organizationId" }
            }
    }

    @Test
    fun `organization package does not depend on billing`() {
        scope.classes()
            .withNameEndingWith("..organization..")
            .assertFalse {
                it.containingFile.imports.any { imp -> imp.name.contains(".billing.") }
            }
    }

    @Test
    fun `services are annotated with Service or Component`() {
        scope.classes()
            .withNameEndingWith("Service")
            .withoutPackage("..core..")
            .assertTrue {
                it.hasAnnotationWithName("Service") || it.hasAnnotationWithName("Component")
            }
    }

    @Test
    fun `repositories are interfaces`() {
        // Konsist 0.16: scope.classes() returns concrete classes only (not interfaces),
        // so any class named *Repository is by definition not an interface — must be empty.
        scope.classes()
            .withNameEndingWith("Repository")
            .assertTrue { false }
    }
}
