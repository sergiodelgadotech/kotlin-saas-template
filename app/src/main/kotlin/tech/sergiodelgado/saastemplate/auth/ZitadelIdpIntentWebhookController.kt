package tech.sergiodelgado.saastemplate.auth

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tech.sergiodelgado.saastemplate.account.AvatarSource
import tech.sergiodelgado.saastemplate.account.UserAccountService

/**
 * Zitadel Actions v2 `restCall` handler for the `RetrieveIdentityProviderIntent` response.
 *
 * Registered with `interruptOnError: false` so any failure here never blocks social login.
 * Zitadel replaces the response with what this endpoint returns, so the full `response` node
 * must always be echoed back (we operate on the raw [JsonNode] tree to preserve unknown fields).
 *
 * Responsibilities:
 *  1. **Username injection** — when `idpInformation.userName` is blank (Slack omits
 *     `preferred_username`), sets it from the IDP email so `AddIDPLink.UserName` passes
 *     validation and the new-user completion form is pre-filled (fixes #170).
 *  2. **Avatar capture** — when `rawInformation` contains a picture URL, persists it via
 *     `UserAccountService` (see #149).
 *  3. **Microsoft/Entra binary avatar** — fetches `/me/photo/$value` via Graph and stores
 *     the bytes in `AvatarStore` (see #167).
 */
@RestController
@RequestMapping("/internal/zitadel")
class ZitadelIdpIntentWebhookController(
    private val userAccountService: UserAccountService,
    private val microsoftGraphAvatarClient: MicrosoftGraphAvatarClient,
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
        //    Sets idpInformation.userName (protojson camelCase) so Zitadel passes a valid
        //    providedUserName to AddIDPLink (auto-link) and pre-fills the new-user form.
        if (idpInfo != null) {
            val currentUsername = idpInfo.path("userName").asText("").trim()
            if (currentUsername.isBlank()) {
                val email = userNode?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
                    ?: rawInfo?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
                if (email != null) {
                    idpInfo.put("userName", email)
                    log.debug("Injected userName '{}' from IDP email for user '{}'",
                        email, response.path("userId").asText("?"))
                }
            }
        }

        // 2. Avatar capture — keyed by the real email claim because the Zitadel user ID in
        //    the webhook envelope does not match the subject the app receives for new users
        //    (the final Zitadel ID is assigned after the action fires). The key must equal
        //    the OIDC `email` the app sees later, so we must NOT use idpInformation.userName:
        //    for some providers (GitHub) that is a login handle ("serandel"), not an email.
        // Microsoft/Entra uses `mail` / `userPrincipalName` rather than `email`.
        // Both the nested User key and top-level rawInfo positions are checked defensively.
        val email = userNode?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
            ?: rawInfo?.path("email")?.asText("")?.takeIf { it.isNotBlank() }
            ?: userNode?.path("mail")?.asText("")?.takeIf { it.isNotBlank() }
            ?: rawInfo?.path("mail")?.asText("")?.takeIf { it.isNotBlank() }
            ?: userNode?.path("userPrincipalName")?.asText("")?.takeIf { it.isNotBlank() }
            ?: rawInfo?.path("userPrincipalName")?.asText("")?.takeIf { it.isNotBlank() }
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

        // 3. Microsoft/Entra binary avatar — Graph /me/photo/$value (no photo URL exposed).
        //    Zitadel does not include idpName in the webhook envelope, so Microsoft is detected
        //    by the presence of `mail` or `userPrincipalName` in rawInformation — these are
        //    Microsoft Graph-specific field names; neither Google (uses email) nor GitHub
        //    (uses email/avatar_url) include them.
        //    idpInformation.oauth.accessToken carries the IDP token; User.Read scope is already
        //    requested so /me/photo/$value is accessible.
        val isMicrosoft = rawInfo?.path("mail")?.asText("")?.isNotBlank() == true ||
            rawInfo?.path("userPrincipalName")?.asText("")?.isNotBlank() == true
        if (isMicrosoft && email != null) {
            val accessToken = idpInfo?.path("oauth")?.path("accessToken")?.asText("")
                ?.takeIf { it.isNotBlank() }
            if (accessToken != null) {
                try {
                    val photo = microsoftGraphAvatarClient.fetchPhoto(accessToken)
                    if (photo != null) {
                        userAccountService.syncAvatarBytesFromIdp(
                            email, photo.contentType, photo.bytes, AvatarSource.IDP
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Microsoft Graph avatar sync failed for email {}: {}", email, e.message)
                }
            }
        }

        return ResponseEntity.ok(response as JsonNode)
    }
}
