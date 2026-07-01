package tech.sergiodelgado.saastemplate.auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
    fun handleIdpPicture(@RequestBody payload: IdpPicturePayload): ResponseEntity<Void> {
        val userId = payload.userId ?: return ResponseEntity.ok().build()
        val picture = payload.idpInformation?.rawInformation?.get("picture") as? String
        if (picture != null) {
            memberRepository.updateAvatarUrl(userId, picture)
            log.debug("Updated avatar for user {}", userId)
        }
        return ResponseEntity.ok().build()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdpPicturePayload(
    val userId: String? = null,
    val idpInformation: IdpInformation? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdpInformation(
    val rawInformation: Map<String, Any?>? = null,
)
