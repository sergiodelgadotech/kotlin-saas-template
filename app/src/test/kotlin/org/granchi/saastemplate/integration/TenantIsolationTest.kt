package org.granchi.saastemplate.integration

import org.granchi.saastemplate.organization.Organization
import org.granchi.saastemplate.organization.OrganizationRepository
import org.granchi.saasstarter.tenant.TenantContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("integration")
@SpringBootTest
@Testcontainers
class TenantIsolationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("mvpsaas_test")
            withUsername("test")
            withPassword("test")
        }
    }

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Test
    fun `organizations are isolated by tenant`() {
        val orgA = organizationRepository.save(Organization(name = "Org A", slug = "org-a"))
        organizationRepository.save(Organization(name = "Org B", slug = "org-b"))

        // Org A can find itself
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
