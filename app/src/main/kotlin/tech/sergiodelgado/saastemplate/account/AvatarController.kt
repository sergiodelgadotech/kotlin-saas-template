package tech.sergiodelgado.saastemplate.account

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * Serves the authenticated user's avatar with the following precedence:
 *
 *  1. **Stored binary** (AvatarStore) — content-type + ETag (`updated_at` epoch) +
 *     304 on If-None-Match + Cache-Control: private
 *  2. **External URL** (`Member.avatarUrl`) — same-origin proxy,
 *     Cache-Control: private (was incorrectly `public` — fixed here)
 *  3. **404** — no avatar available
 *
 * The `?v=<epoch>` query parameter used by [tech.sergiodelgado.saastemplate.config.NavModelAdvice]
 * for cache-busting is intentionally ignored here; it only forces a new browser request
 * past a stale cached response. The ETag/304 logic handles conditional requests from there.
 */
@Controller
class AvatarController(private val userAccountService: UserAccountService) {

    @GetMapping("/avatar")
    fun avatar(
        @AuthenticationPrincipal principal: OidcUser,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val subject = principal.subject
        if (subject == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        // 1. Stored binary avatar takes precedence over the external URL.
        // Guard with isPresent() to mirror NavModelAdvice — if TenantContext is unset
        // (shouldn't happen if /avatar is in saasstarter.tenant.path-patterns, but
        // defense-in-depth so a future config gap degrades to URL/404 instead of 500).
        val stored = if (TenantContext.isPresent()) userAccountService.getStoredAvatar(subject) else null
        if (stored != null) {
            val etag = "\"${stored.updatedAt.toEpochMilli()}\""
            if (request.getHeader("If-None-Match") == etag) {
                response.status = HttpServletResponse.SC_NOT_MODIFIED
                return
            }
            response.contentType = stored.contentType
            response.addHeader("ETag", etag)
            response.addHeader("Cache-Control", "private, max-age=86400")
            response.outputStream.write(stored.bytes)
            return
        }

        // 2. Fall back to proxying the external avatar URL.
        val url = userAccountService.getAvatarUrl(subject)
        if (url == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        response.contentType = conn.contentType ?: "image/jpeg"
        // private: this URL returns different bytes per user — shared caches must not cache it.
        response.addHeader("Cache-Control", "private, max-age=86400")
        conn.inputStream.use { it.copyTo(response.outputStream) }
    }
}
