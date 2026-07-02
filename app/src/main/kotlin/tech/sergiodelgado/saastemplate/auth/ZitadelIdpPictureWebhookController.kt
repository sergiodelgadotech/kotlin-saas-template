package tech.sergiodelgado.saastemplate.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tech.sergiodelgado.saasstarter.organization.MemberRepository

@RestController
@RequestMapping("/internal/zitadel")
class ZitadelIdpPictureWebhookController(
    private val memberRepository: MemberRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/idp-picture")
    fun handleIdpPicture(@RequestBody payload: ZitadelExecutionPayload): ResponseEntity<Void> {
        val userId = payload.response?.userId ?: return ResponseEntity.ok().build()
        val raw = payload.response.idpInformation?.rawInformation
        // Zitadel wraps the IDP userinfo response under a "User" key in rawInformation.
        // OIDC providers (Google, Microsoft, Slack) expose "picture"; GitHub OAuth2 uses "avatar_url".
        @Suppress("UNCHECKED_CAST")
        val userMap = raw?.get("User") as? Map<String, Any?>
        val picture = userMap?.get("picture") as? String
            ?: userMap?.get("avatar_url") as? String
            ?: raw?.get("picture") as? String
            ?: raw?.get("avatar_url") as? String
            ?: return ResponseEntity.ok().build()
        memberRepository.updateAvatarUrl(userId, picture)
        log.info("Updated avatar for user {}", userId)
        return ResponseEntity.ok().build()
    }
}

// Zitadel sends a ContextInfoResponse envelope; the actual gRPC response is nested under "response".
@JsonIgnoreProperties(ignoreUnknown = true)
data class ZitadelExecutionPayload(
    @JsonProperty("userID") val contextUserId: String? = null,
    val response: IdpIntentResponse? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdpIntentResponse(
    val userId: String? = null,
    val idpInformation: IdpInformation? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdpInformation(
    val rawInformation: Map<String, Any?>? = null,
)
