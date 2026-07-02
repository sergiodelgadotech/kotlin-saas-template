package tech.sergiodelgado.saastemplate.account

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.MemberRepository

data class AccountProfile(val firstName: String, val lastName: String)

@Service
class UserAccountService(
    private val idpUserDirectory: IdpUserDirectory,
    private val memberRepository: MemberRepository,
) {
    // Keyed by the user's email. The webhook fires before the Zitadel user ID is finalised
    // for new users, so we can't key by Zitadel subject here. Email is stable and available
    // at both ends: the webhook reads it from the IDP claim, and onboarding/success-handler
    // read it from the OIDC token.
    private val pendingAvatarUrls = ConcurrentHashMap<String, String>()

    fun getProfile(userId: String): AccountProfile {
        val member = memberRepository.findByExternalUserId(userId)
        return AccountProfile(
            firstName = member?.firstName.orEmpty(),
            lastName = member?.lastName.orEmpty(),
        )
    }

    fun getAvatarUrl(userId: String): String? =
        memberRepository.findByExternalUserId(userId)?.avatarUrl?.takeIf { it.isNotBlank() }

    fun updateAvatarUrl(userId: String, avatarUrl: String) =
        memberRepository.updateAvatarUrl(userId, avatarUrl)

    // Called by the IDP webhook; always buffers by email because at webhook time
    // the final Zitadel user ID may not exist yet (new-user creation flow).
    fun syncAvatarFromIdp(email: String, avatarUrl: String) {
        pendingAvatarUrls[email] = avatarUrl
    }

    // Drains the pending entry keyed by email. Called from:
    //  - OnboardingService.createOrganization  (new users — email param is available)
    //  - ZitadelAuthenticationSuccessHandler   (returning users — oidcUser.email)
    fun consumePendingAvatarUrl(email: String): String? = pendingAvatarUrls.remove(email)

    fun updateDisplayName(userId: String, givenName: String, familyName: String, email: String) {
        idpUserDirectory.updateProfile(userId, givenName, familyName)
        memberRepository.updateProfile(userId, email, givenName, familyName)
    }
}
