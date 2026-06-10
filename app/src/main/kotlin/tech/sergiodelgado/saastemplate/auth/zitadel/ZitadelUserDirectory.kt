package tech.sergiodelgado.saastemplate.auth.zitadel

import com.zitadel.ApiException
import com.zitadel.Zitadel
import com.zitadel.model.BetaUserServiceAddHumanUserRequest
import com.zitadel.model.BetaUserServiceEmailQuery
import com.zitadel.model.BetaUserServiceListUsersRequest
import com.zitadel.model.BetaUserServiceSearchQuery
import com.zitadel.model.BetaUserServiceSendEmailVerificationCode
import com.zitadel.model.BetaUserServiceSetHumanEmail
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory

/**
 * Zitadel-backed implementation of [IdpUserDirectory].
 *
 * Looks up an IdP user by email. If the user exists, returns their Zitadel `userId` (= `sub`).
 * If not, creates a human user with email-only registration and triggers Zitadel's built-in
 * invitation email, then returns the newly created `userId`.
 *
 * Only registered when `saastemplate.zitadel.management.pat` is set, so tests can inject
 * a stub implementation without needing a live Zitadel instance.
 */
@Component
@ConditionalOnProperty(name = ["saastemplate.zitadel.management.pat"], matchIfMissing = false)
class ZitadelUserDirectory(
    private val properties: ZitadelManagementProperties
) : IdpUserDirectory {

    private val zitadel: Zitadel by lazy {
        Zitadel.withAccessToken(properties.baseUrl, properties.pat)
    }

    @Throws(ApiException::class)
    override fun findOrInvite(email: String): String {
        val emailFilter = BetaUserServiceSearchQuery().emailQuery(
            BetaUserServiceEmailQuery().emailAddress(email)
        )
        val listRequest = BetaUserServiceListUsersRequest().queries(listOf(emailFilter))
        val response = zitadel.getBetaUsers().listUsers(listRequest)

        val existing = response.result?.firstOrNull()
        if (existing != null) {
            return requireNotNull(existing.userId) {
                "Zitadel returned a user with null userId for email: $email"
            }
        }

        val createResponse = zitadel.getBetaUsers().addHumanUser(
            BetaUserServiceAddHumanUserRequest()
                .email(
                    BetaUserServiceSetHumanEmail()
                        .email(email)
                        .sendCode(BetaUserServiceSendEmailVerificationCode())
                        .isVerified(false)
                )
        )

        return requireNotNull(createResponse.userId) {
            "Zitadel returned null userId after creating user for email: $email"
        }
    }
}
