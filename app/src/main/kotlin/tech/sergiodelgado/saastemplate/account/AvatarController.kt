package tech.sergiodelgado.saastemplate.account

import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import java.net.HttpURLConnection
import java.net.URI

@Controller
class AvatarController(private val memberRepository: MemberRepository) {

    @GetMapping("/avatar")
    fun avatar(
        @AuthenticationPrincipal principal: OidcUser,
        response: HttpServletResponse,
    ) {
        val url = principal.subject?.let { memberRepository.findByExternalUserId(it)?.avatarUrl }
        if (url.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        response.contentType = conn.contentType ?: "image/jpeg"
        response.addHeader("Cache-Control", "public, max-age=86400")
        conn.inputStream.use { it.copyTo(response.outputStream) }
    }
}
