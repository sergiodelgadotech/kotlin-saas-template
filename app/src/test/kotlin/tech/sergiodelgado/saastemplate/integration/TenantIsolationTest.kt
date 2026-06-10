package tech.sergiodelgado.saastemplate.integration

import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import tech.sergiodelgado.saasstarter.organization.OrganizationService
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.none

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

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var organizationService: OrganizationService

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

    @Test
    fun `member invited to org A does not appear in org B's member list`() {
        val orgA = organizationRepository.save(Organization(name = "Iso Org A", slug = "iso-org-a"))
        val orgB = organizationRepository.save(Organization(name = "Iso Org B", slug = "iso-org-b"))

        // Seed an OWNER for orgA so the invite auth check passes
        memberRepository.save(
            tech.sergiodelgado.saasstarter.organization.Member(
                organizationId = orgA.id,
                externalUserId = "isolation-test-owner",
                role = "OWNER",
            )
        )

        // Authenticate as that owner
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                "isolation-test-owner",
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )

        try {
            TenantContext.set(orgA.id)
            organizationService.inviteMember("invited-user-sub", "MEMBER")
        } finally {
            SecurityContextHolder.clearContext()
            TenantContext.clear()
        }

        // Org B must not see the member invited into org A
        val orgBMembers = memberRepository.findByOrganizationId(orgB.id)
        expectThat(orgBMembers).none { get { externalUserId }.isEqualTo("invited-user-sub") }
    }
}
