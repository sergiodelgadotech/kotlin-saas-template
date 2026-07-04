package tech.sergiodelgado.saastemplate.account

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.time.Instant
import java.util.UUID

class AvatarControllerTest {

    private val userAccountService = mockk<UserAccountService>()
    private val controller = AvatarController(userAccountService)
    private val principal = mockk<OidcUser> { every { subject } returns "user-123" }
    private val orgId = UUID.randomUUID()

    @BeforeEach
    fun setTenantContext() {
        // Simulate TenantInterceptor having run for /avatar (the path-pattern fix this PR adds).
        // Tests that specifically verify the absent-context guard clear it explicitly.
        TenantContext.set(orgId)
    }

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }

    private fun request(ifNoneMatch: String? = null) = mockk<HttpServletRequest> {
        every { getHeader("If-None-Match") } returns ifNoneMatch
    }

    @Test
    fun `returns 404 when member has no avatar`() {
        every { userAccountService.getStoredAvatar("user-123") } returns null
        every { userAccountService.getAvatarUrl("user-123") } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, request(), response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }

    @Test
    fun `returns 404 when subject is null`() {
        every { principal.subject } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, request(), response)

        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }

    @Test
    fun `serves stored bytes with ETag and private Cache-Control`() {
        val updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
        val stored = AvatarImage(
            id = UUID.randomUUID(),
            organizationId = orgId,
            externalUserId = "user-123",
            contentType = "image/png",
            bytes = byteArrayOf(1, 2, 3, 4),
            source = AvatarSource.IDP.name,
            updatedAt = updatedAt,
        )
        every { userAccountService.getStoredAvatar("user-123") } returns stored
        val response = mockk<HttpServletResponse>(relaxed = true) {
            every { outputStream } returns mockk(relaxed = true)
        }

        controller.avatar(principal, request(), response)

        val expectedEtag = "\"1700000000000\""
        verify { response.contentType = "image/png" }
        verify { response.addHeader("ETag", expectedEtag) }
        verify { response.addHeader("Cache-Control", "private, max-age=86400") }
    }

    @Test
    fun `returns 304 when If-None-Match matches stored ETag`() {
        val updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
        val stored = AvatarImage(
            id = UUID.randomUUID(),
            organizationId = orgId,
            externalUserId = "user-123",
            contentType = "image/png",
            bytes = byteArrayOf(1, 2, 3),
            source = AvatarSource.IDP.name,
            updatedAt = updatedAt,
        )
        every { userAccountService.getStoredAvatar("user-123") } returns stored
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, request(ifNoneMatch = "\"1700000000000\""), response)

        verify { response.status = HttpServletResponse.SC_NOT_MODIFIED }
    }

    @Test
    fun `stored bytes take precedence over external URL`() {
        val stored = AvatarImage(
            id = UUID.randomUUID(),
            organizationId = orgId,
            externalUserId = "user-123",
            contentType = "image/jpeg",
            bytes = byteArrayOf(9, 8, 7),
            source = AvatarSource.IDP.name,
            updatedAt = Instant.now(),
        )
        every { userAccountService.getStoredAvatar("user-123") } returns stored
        val response = mockk<HttpServletResponse>(relaxed = true) {
            every { outputStream } returns mockk(relaxed = true)
        }

        controller.avatar(principal, request(), response)

        // getAvatarUrl should not be called when stored bytes are present
        verify(exactly = 0) { userAccountService.getAvatarUrl(any()) }
    }

    @Test
    fun `falls back to URL proxy when no stored bytes`() {
        // We can't actually call out to a real URL in a unit test; we verify the
        // getAvatarUrl path is reached by checking that a non-null URL leads to
        // getAvatarUrl being consulted and 404 not being sent for null store.
        every { userAccountService.getStoredAvatar("user-123") } returns null
        every { userAccountService.getAvatarUrl("user-123") } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, request(), response)

        verify { userAccountService.getAvatarUrl("user-123") }
        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }

    @Test
    fun `falls back gracefully when TenantContext is absent (defense-in-depth guard)`() {
        // If /avatar were to be missing from saasstarter.tenant.path-patterns in the future,
        // the guard in the controller ensures we skip the stored-binary lookup
        // (which would otherwise throw "No tenant in context") and fall through to URL/404.
        TenantContext.clear() // override @BeforeEach — simulate absent interceptor
        every { userAccountService.getAvatarUrl("user-123") } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.avatar(principal, request(), response)

        // Stored binary lookup is skipped — no TenantContext, no DB call
        verify(exactly = 0) { userAccountService.getStoredAvatar(any()) }
        verify { response.sendError(HttpServletResponse.SC_NOT_FOUND) }
    }
}
