package tech.sergiodelgado.saastemplate.auth

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tech.sergiodelgado.saastemplate.account.UserAccountService

/**
 * Zitadel Actions v2 `restWebhook` handler for the `RetrieveIdentityProviderIntent` response.
 *
 * Registered as a fire-and-forget `restWebhook` target — Zitadel ignores the response body,
 * so this endpoint can never break social login regardless of what it returns.
 *
 * Responsibilities:
 *  1. **Avatar capture** — when `rawInformation` contains a picture URL, persist it via
 *     `UserAccountService` (see #149).
 *  2. **Username injection** (attempted, no effect with restWebhook) — when
 *     `idpInformation.username` is blank the email is placed back into the response node,
 *     but Zitadel discards the body so this does not fix Slack auto-link (#153).
 *     Switching to `restCall` to make the injection effective caused `missing_idp_info`
 *     for all providers — root cause is under investigation.
 *
 * We still operate on the raw [JsonNode] tree so the code is ready if we re-enable
 * `restCall` in a future iteration.
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

        // 2. Avatar capture — keyed by the real email claim because the Zitadel user ID in
        //    the webhook envelope does not match the subject the app receives for new users
        //    (the final Zitadel ID is assigned after the action fires). The key must equal
        //    the OIDC `email` the app sees later, so we must NOT use idpInformation.userName:
        //    for some providers (GitHub) that is a login handle ("serandel"), not an email.
        val email = userNode?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
            ?: rawInfo?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
            ?: response.path("addHumanUser").path("email").path("email").asText("").takeIf { it.isNotBlank() }
            ?: response.path("createUser").path("human").path("email").path("email").asText("").takeIf { it.isNotBlank() }
        val picture = userNode?.path("picture")?.asText("")?.takeIf { it.isNotBlank() }
            ?: userNode?.path("avatar_url")?.asText("")?.takeIf { it.isNotBlank() }
            ?: rawInfo?.path("picture")?.asText("")?.takeIf { it.isNotBlank() }
            ?: rawInfo?.path("avatar_url")?.asText("")?.takeIf { it.isNotBlank() }
        if (email != null && picture != null) {
            try {
                userAccountService.syncAvatarFromIdp(email, picture)
            } catch (e: Exception) {
                log.warn("Avatar sync failed for email {}: {}", email, e.message)
            }
        }

        return ResponseEntity.ok(response as JsonNode)
    }
}
