package org.granchi.saastemplate.integration

import org.granchi.saastemplate.SaasTemplateApplication
import org.granchi.saasstarter.organization.Organization
import org.granchi.saasstarter.organization.OrganizationRepository
import org.granchi.saasstarter.tenant.TenantContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("integration")
@Testcontainers
// Explicit class avoids ambiguity with @SpringBootApplication inner classes in this package.
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class TenantIsolationTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Test
    fun `organizations are isolated by tenant`() {
        val orgA = organizationRepository.save(Organization(name = "Org A", slug = "org-a"))
        organizationRepository.save(Organization(name = "Org B", slug = "org-b"))

        TenantContext.set(orgA.id)
        val foundA = organizationRepository.findById(TenantContext.get())
        TenantContext.clear()

        expectThat(foundA.get().name).isEqualTo("Org A")
    }

    @Test
    fun `accessing tenant context without setting it throws`() {
        TenantContext.clear()
        assertThrows<IllegalStateException> {
            TenantContext.get()
        }.also {
            expectThat(it.message).isEqualTo("No tenant in context")
        }
    }
}
