package tech.sergiodelgado.saastemplate.integration

import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import tech.sergiodelgado.saasstarter.organization.OrganizationService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class OrganizationAutoConfigSmokeTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var organizationService: OrganizationService

    @Test
    fun `OrganizationRepository, MemberRepository and OrganizationService are wired from starter`() {
        expectThat(context.getBeansOfType(OrganizationRepository::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(MemberRepository::class.java)).hasSize(1)
        expectThat(context.getBeansOfType(OrganizationService::class.java)).hasSize(1)
    }

    @Test
    fun `member tenant resolution finds organization for an external user`() {
        val org = organizationRepository.save(Organization(name = "Smoke", slug = "smoke-org"))
        memberRepository.save(Member(organizationId = org.id, externalUserId = "smoke-user"))

        val resolvedOrgId = memberRepository.findOrganizationIdByUserId("smoke-user")
        expectThat(resolvedOrgId).isNotNull().isEqualTo(org.id)
    }
}
