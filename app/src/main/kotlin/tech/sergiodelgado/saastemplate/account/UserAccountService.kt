package tech.sergiodelgado.saastemplate.account

import org.springframework.stereotype.Service
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.MemberRepository

data class AccountProfile(val firstName: String, val lastName: String)

@Service
class UserAccountService(
    private val idpUserDirectory: IdpUserDirectory,
    private val memberRepository: MemberRepository,
) {
    fun getProfile(userId: String): AccountProfile {
        val member = memberRepository.findByExternalUserId(userId)
        return AccountProfile(
            firstName = member?.firstName.orEmpty(),
            lastName = member?.lastName.orEmpty(),
        )
    }

    fun updateDisplayName(userId: String, givenName: String, familyName: String, email: String) {
        idpUserDirectory.updateProfile(userId, givenName, familyName)
        memberRepository.updateProfile(userId, email, givenName, familyName)
    }
}
