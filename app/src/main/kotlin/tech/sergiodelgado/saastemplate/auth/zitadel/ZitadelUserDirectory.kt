package tech.sergiodelgado.saastemplate.auth.zitadel

import com.zitadel.ApiException
import com.zitadel.api.UserServiceApi
import com.zitadel.model.UserServiceAddHumanUserRequest
import com.zitadel.model.UserServiceCreateInviteCodeRequest
import com.zitadel.model.UserServiceEmailQuery
import com.zitadel.model.UserServiceListUsersRequest
import com.zitadel.model.UserServiceOrganization
import com.zitadel.model.UserServiceOrganizationIdQuery
import com.zitadel.model.UserServiceSearchQuery
import com.zitadel.model.UserServiceSendInviteCode
import com.zitadel.model.UserServiceSetHumanEmail
import com.zitadel.model.UserServiceSetHumanProfile
import com.zitadel.model.UserServiceUpdateHumanUserRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory

/**
 * Zitadel-backed implementation of [IdpUserDirectory].
 *
 * Looks up an IdP user by email. If the user exists, returns their Zitadel `userId` (= `sub`).
 * If not, creates a human user with no pre-set names (Zitadel collects first/last name during
 * the invite-code onboarding form), triggers an invite-code email with a `urlTemplate` that
 * redirects the invitee back to the app's OIDC sign-in entry point after they finish, and
 * returns the newly created `userId`.
 *
 * Only registered when `saastemplate.zitadel.management.pat` is set, so tests can inject
 * a stub implementation without needing a live Zitadel instance.
 */
@Component
@ConditionalOnProperty(name = ["saastemplate.zitadel.management.pat"])
class ZitadelUserDirectory(
    private val properties: ZitadelManagementProperties,
    private val userService: UserServiceApi,
    @Value("\${app.base-url}") private val appBaseUrl: String,
) : IdpUserDirectory {

    override fun findOrInvite(email: String): String {
        val emailFilter = UserServiceSearchQuery().emailQuery(
            UserServiceEmailQuery().emailAddress(email)
        )
        val orgFilter = UserServiceSearchQuery().organizationIdQuery(
            UserServiceOrganizationIdQuery().organizationId(properties.organizationId)
        )
        val listRequest = UserServiceListUsersRequest().queries(listOf(emailFilter, orgFilter))
        try {
            val response = userService.listUsers(listRequest)

            val existing = response.result?.firstOrNull()
            if (existing != null) {
                return requireNotNull(existing.userId) {
                    "Zitadel returned a user with null userId for email: $email"
                }
            }

            val (givenName, familyName) = namesFromEmail(email)
            val createResponse = userService.addHumanUser(
                UserServiceAddHumanUserRequest()
                    .organization(
                        UserServiceOrganization().orgId(properties.organizationId)
                    )
                    .profile(
                        UserServiceSetHumanProfile()
                            .givenName(givenName)
                            .familyName(familyName)
                    )
                    .email(
                        UserServiceSetHumanEmail()
                            .email(email)
                            .isVerified(true)
                    )
            )

            val newUserId = requireNotNull(createResponse.userId) {
                "Zitadel returned null userId after creating user for email: $email"
            }

            userService.createInviteCode(
                UserServiceCreateInviteCodeRequest()
                    .userId(newUserId)
                    .sendCode(
                        UserServiceSendInviteCode()
                            .urlTemplate("$appBaseUrl/sign-in")
                            .applicationName(properties.applicationName)
                    )
            )

            return newUserId
        } catch (e: ApiException) {
            throw IllegalStateException("Zitadel API error (HTTP ${e.code}): ${e.responseBody}", e)
        } catch (e: IllegalArgumentException) {
            throw e  // requireNotNull failures — programmer error, not a transport error
        } catch (e: RuntimeException) {
            throw IllegalStateException("Zitadel connection error: ${e.message}", e)
        }
    }

    override fun updateProfile(userId: String, givenName: String, familyName: String) {
        try {
            userService.updateHumanUser(
                UserServiceUpdateHumanUserRequest()
                    .userId(userId)
                    .profile(
                        UserServiceSetHumanProfile()
                            .givenName(givenName)
                            .familyName(familyName)
                    )
            )
        } catch (e: ApiException) {
            throw IllegalStateException("Zitadel API error (HTTP ${e.code}): ${e.responseBody}", e)
        } catch (e: RuntimeException) {
            throw IllegalStateException("Zitadel connection error: ${e.message}", e)
        }
    }

    // Derives a placeholder given/family name from the email local-part so Zitadel's
    // 1-200 rune validation passes. The invitee corrects their name during onboarding.
    private fun namesFromEmail(email: String): Pair<String, String> {
        val local = email.substringBefore("@")
        val parts = local.split(Regex("[._+\\-]")).filter { it.isNotEmpty() }
            .map { it.replaceFirstChar(Char::uppercase) }
        return if (parts.size >= 2) {
            parts.first() to parts.drop(1).joinToString(" ")
        } else {
            val name = parts.firstOrNull() ?: local.replaceFirstChar(Char::uppercase)
            name to name
        }
    }
}
