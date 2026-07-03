package tech.sergiodelgado.saastemplate.account

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import tech.sergiodelgado.saastemplate.SaasTemplateApplication
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import tech.sergiodelgado.saasstarter.tenant.TenantContext

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = [SaasTemplateApplication::class])
@ActiveProfiles("test")
class PostgresAvatarStoreIntegrationTest {

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    private lateinit var avatarStore: AvatarStore

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    private lateinit var org: Organization

    @BeforeEach
    fun setUp() {
        org = organizationRepository.save(Organization(name = "AvatarOrg", slug = "avatar-org-${System.nanoTime()}"))
        TenantContext.set(org.id)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `put and get round-trip preserves bytes and content-type`() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47) // PNG magic
        avatarStore.put("ext-user-1", "image/png", bytes, AvatarSource.IDP)

        val result = avatarStore.get("ext-user-1")

        expectThat(result).isNotNull()
        expectThat(result!!.contentType).isEqualTo("image/png")
        expectThat(result.bytes.contentEquals(bytes)).isEqualTo(true)
        expectThat(result.source).isEqualTo(AvatarSource.IDP.name)
    }

    @Test
    fun `get returns null when no image stored`() {
        expectThat(avatarStore.get("no-such-user")).isNull()
    }

    @Test
    fun `delete removes the stored image`() {
        avatarStore.put("ext-user-2", "image/jpeg", byteArrayOf(1, 2, 3), AvatarSource.IDP)
        avatarStore.delete("ext-user-2")

        expectThat(avatarStore.get("ext-user-2")).isNull()
    }

    @Test
    fun `put overwrites an existing IDP image with a newer IDP image`() {
        val first = byteArrayOf(1)
        val second = byteArrayOf(2, 3)
        avatarStore.put("ext-user-3", "image/jpeg", first, AvatarSource.IDP)
        avatarStore.put("ext-user-3", "image/png", second, AvatarSource.IDP)

        val result = avatarStore.get("ext-user-3")
        expectThat(result?.contentType).isEqualTo("image/png")
        expectThat(result?.bytes?.contentEquals(second)).isEqualTo(true)
    }

    @Test
    fun `IDP put does not clobber an UPLOAD image`() {
        val uploadBytes = byteArrayOf(10, 20, 30)
        avatarStore.put("ext-user-4", "image/png", uploadBytes, AvatarSource.UPLOAD)

        // IDP re-sync should be ignored
        avatarStore.put("ext-user-4", "image/jpeg", byteArrayOf(99.toByte()), AvatarSource.IDP)

        val result = avatarStore.get("ext-user-4")
        expectThat(result?.source).isEqualTo(AvatarSource.UPLOAD.name)
        expectThat(result?.bytes?.contentEquals(uploadBytes)).isEqualTo(true)
    }

    @Test
    fun `UPLOAD put overwrites an IDP image`() {
        avatarStore.put("ext-user-5", "image/jpeg", byteArrayOf(1), AvatarSource.IDP)

        val uploadBytes = byteArrayOf(2, 3, 4)
        avatarStore.put("ext-user-5", "image/png", uploadBytes, AvatarSource.UPLOAD)

        val result = avatarStore.get("ext-user-5")
        expectThat(result?.source).isEqualTo(AvatarSource.UPLOAD.name)
        expectThat(result?.bytes?.contentEquals(uploadBytes)).isEqualTo(true)
    }

    @Test
    fun `get is scoped to the current tenant — different org cannot see another org image`() {
        // Store image in org A (currently active)
        avatarStore.put("shared-user", "image/jpeg", byteArrayOf(42), AvatarSource.IDP)

        // Switch to a different org
        val orgB = organizationRepository.save(Organization(name = "OrgB", slug = "org-b-${System.nanoTime()}"))
        TenantContext.set(orgB.id)

        expectThat(avatarStore.get("shared-user")).isNull()
    }
}
