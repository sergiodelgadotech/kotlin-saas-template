package tech.sergiodelgado.saastemplate.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/** Binary avatar bytes returned from Microsoft Graph `/me/photo/$value`. */
data class AvatarBytes(val contentType: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AvatarBytes) return false
        return contentType == other.contentType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * Fetches binary profile photos from Microsoft Graph.
 * Used by [ZitadelIdpIntentWebhookController] to buffer Microsoft/Entra avatars (see #160).
 *
 * Microsoft/Entra does not expose a photo URL, so the avatar must be fetched as binary
 * from Graph using the OAuth access token present in the IDP-intent webhook envelope.
 */
@Component
class MicrosoftGraphAvatarClient(
    @Qualifier("microsoftGraphRestClient") private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Fetches `GET /me/photo/$value` using the given access token.
     * Returns null on Graph 404 (no photo set), empty body, or any failure — best-effort.
     */
    fun fetchPhoto(accessToken: String): AvatarBytes? = try {
        log.info("[MS Avatar diag] Calling Graph GET /me/photo/\$value")
        val entity = restClient.get()
            .uri("/me/photo/\$value")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .toEntity(ByteArray::class.java)
        val body = entity.body
        log.info("[MS Avatar diag] Graph response: status={} contentType={} bodyBytes={}",
            entity.statusCode, entity.headers.contentType, body?.size ?: "(null)")
        val bytes = body?.takeIf { it.isNotEmpty() }
            ?: run { log.info("[MS Avatar diag] Graph body was null or empty — returning null"); return null }
        val contentType = entity.headers.contentType?.toString() ?: "image/jpeg"
        AvatarBytes(contentType, bytes)
    } catch (e: HttpClientErrorException.NotFound) {
        log.info("[MS Avatar diag] Graph returned 404 for /me/photo/\$value — no photo set")
        null
    } catch (e: Exception) {
        log.warn("[MS Avatar diag] Failed to fetch Microsoft Graph photo: {} ({})", e.message, e.javaClass.simpleName)
        null
    }
}
