package tech.sergiodelgado.saastemplate.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tech.sergiodelgado.saastemplate.account.UserAccountService

/**
 * Zitadel Actions v2 `restCall` handler for the `RetrieveIdentityProviderIntent` response.
 *
 * Registered as a `restCall` target (not `restWebhook`) so Zitadel applies the returned JSON
 * as the modified response.  `interruptOnError: false` on the target means that if this
 * endpoint errors Zitadel falls back to the original response — a bug here cannot break
 * social login for all providers.
 *
 * Responsibilities (merged because only one execution can be registered per condition):
 *  1. **Username injection** — when `idpInformation.username` is blank (Slack does not
 *     return `preferred_username`), set it from the IDP email so Zitadel's `AddIDPLink`
 *     call receives a valid `providedUserName` and auto-link succeeds (fixes #153).
 *  2. **Avatar capture** — when `rawInformation` contains a picture URL, persist it via
 *     `UserAccountService` (preserves #149 behaviour).
 *
 * IMPORTANT: Zitadel uses the returned body as the new response.  We therefore operate on
 * the raw [JsonNode] tree rather than trimmed DTOs — serialising typed DTOs would drop
 * fields we don't model (oauth tokens, addHumanUser, etc.) and break downstream login.
 * The full `response` node is returned unchanged except for the injected username field.
 */
@RestController
@RequestMapping("/internal/zitadel")
class ZitadelIdpIntentWebhookController(
    private val userAccountService: UserAccountService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/idp-intent")
    fun handleIdpIntent(@RequestBody envelope: JsonNode): ResponseEntity<JsonNode> {
        val response = envelope.path("response").takeIf { !it.isMissingNode && !it.isNull }
            as? ObjectNode
            ?: return ResponseEntity.ok().build()

        val idpInfo = response.path("idpInformation").takeIf { !it.isMissingNode }
            as? ObjectNode

        val rawInfo = idpInfo?.path("rawInformation")?.takeIf { !it.isMissingNode }
        // Zitadel wraps the IDP userinfo under a "User" key in rawInformation.
        val userNode = rawInfo?.path("User")?.takeIf { !it.isMissingNode }

        // 1. Username injection — fills the gap when preferred_username is absent (Slack).
        //    We set idpInformation.username so Zitadel passes a valid providedUserName to
        //    AddIDPLink (auto-link) and pre-fills the completion form (new-user flow).
        if (idpInfo != null) {
            val currentUsername = idpInfo.path("username").asText("").trim()
            if (currentUsername.isBlank()) {
                val email = userNode?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
                    ?: rawInfo?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
                if (email != null) {
                    idpInfo.put("username", email)
                    log.debug("Injected username '{}' from IDP email for user '{}'",
                        email, response.path("userId").asText("?"))
                }
            }
        }

        // 2. Avatar capture — unchanged from the original #149 restWebhook behaviour.
        val userId = response.path("userId").asText("").takeIf { it.isNotBlank() }
        if (userId != null) {
            val picture = userNode?.path("picture")?.asText("")?.takeIf { it.isNotBlank() }
                ?: userNode?.path("avatar_url")?.asText("")?.takeIf { it.isNotBlank() }
                ?: rawInfo?.path("picture")?.asText("")?.takeIf { it.isNotBlank() }
                ?: rawInfo?.path("avatar_url")?.asText("")?.takeIf { it.isNotBlank() }
            if (picture != null) {
                try {
                    userAccountService.updateAvatarUrl(userId, picture)
                    log.info("Updated avatar for user {}", userId)
                } catch (e: Exception) {
                    // Avatar update failure must not corrupt the returned response.
                    log.warn("Avatar update failed for user {}: {}", userId, e.message)
                }
            }
        }

        return ResponseEntity.ok(response as JsonNode)
    }
}
