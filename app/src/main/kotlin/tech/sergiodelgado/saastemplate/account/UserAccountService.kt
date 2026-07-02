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
    // Holds picture URLs received from the IDP webhook before the member row is created.
    // The webhook fires during Zitadel's login flow; the member row is only created later
    // during onboarding. consumePendingAvatarUrl() drains the entry when the row is saved.
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

    // Called by the IDP webhook (fires before member row exists for new users).
    // If the member row is already present, updates immediately; otherwise buffers
    // the URL so consumePendingAvatarUrl() can pick it up during org creation.
    fun syncAvatarFromIdp(userId: String, avatarUrl: String) {
        if (memberRepository.findByExternalUserId(userId) != null) {
            memberRepository.updateAvatarUrl(userId, avatarUrl)
        } else {
            pendingAvatarUrls[userId] = avatarUrl
        }
    }

    fun consumePendingAvatarUrl(userId: String): String? = pendingAvatarUrls.remove(userId)

    fun updateDisplayName(userId: String, givenName: String, familyName: String, email: String) {
        idpUserDirectory.updateProfile(userId, givenName, familyName)
        memberRepository.updateProfile(userId, email, givenName, familyName)
    }
}
