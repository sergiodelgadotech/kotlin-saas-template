package tech.sergiodelgado.saastemplate.account

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.MemberRepository

data class AccountProfile(val firstName: String, val lastName: String)

/**
 * Pending binary avatar buffered before the Zitadel user ID is finalised.
 * Mirrors [pendingAvatarUrls] for the binary path (producers arrive in #160/#161).
 */
data class PendingAvatarBytes(
    val contentType: String,
    val bytes: ByteArray,
    val source: AvatarSource,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingAvatarBytes) return false
        return contentType == other.contentType &&
            bytes.contentEquals(other.bytes) &&
            source == other.source
    }

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + source.hashCode()
        return result
    }
}

@Service
class UserAccountService(
    private val idpUserDirectory: IdpUserDirectory,
    private val memberRepository: MemberRepository,
    private val avatarStore: AvatarStore,
) {
    // ── URL-based avatar buffer (keyed by email) ──────────────────────────────
    //
    // Keyed by the user's email. The webhook fires before the Zitadel user ID is finalised
    // for new users, so we can't key by Zitadel subject here. Email is stable and available
    // at both ends: the webhook reads it from the IDP claim, and onboarding/success-handler
    // read it from the OIDC token.
    private val pendingAvatarUrls = ConcurrentHashMap<String, String>()

    // ── Binary avatar buffer (keyed by email) — dormant until #160 / #161 ─────
    //
    // Mirrors pendingAvatarUrls for providers that expose a photo only as binary
    // (e.g. Microsoft/Entra Graph /me/photo/$value). Populated by syncAvatarBytesFromIdp
    // (called from the IDP webhook when bytes are available) and drained at
    // onboarding/login via consumePendingAvatarBytes.
    private val pendingAvatarBytes = ConcurrentHashMap<String, PendingAvatarBytes>()

    fun getProfile(userId: String): AccountProfile {
        val member = memberRepository.findByExternalUserId(userId)
        return AccountProfile(
            firstName = member?.firstName.orEmpty(),
            lastName = member?.lastName.orEmpty(),
        )
    }

    // ── Avatar URL path (existing) ────────────────────────────────────────────

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

    // ── Binary avatar path (AvatarStore-backed) ───────────────────────────────

    /** Returns the stored binary avatar for [userId] in the current tenant, or null. */
    fun getStoredAvatar(userId: String): AvatarImage? = avatarStore.get(userId)

    /** Persists binary avatar bytes for [userId] in the current tenant. */
    fun storeAvatar(userId: String, contentType: String, bytes: ByteArray, source: AvatarSource) =
        avatarStore.put(userId, contentType, bytes, source)

    /**
     * Buffers binary avatar bytes by email for later drain.
     * Called from the IDP webhook when the provider supplies a photo as binary
     * (e.g. Microsoft/Entra Graph). Dormant until #160 starts calling this.
     */
    fun syncAvatarBytesFromIdp(email: String, contentType: String, bytes: ByteArray, source: AvatarSource) {
        pendingAvatarBytes[email] = PendingAvatarBytes(contentType, bytes, source)
    }

    /**
     * Drains and returns the buffered binary avatar for [email], or null.
     * Drain-once semantics mirror [consumePendingAvatarUrl]. Called from:
     *  - OnboardingService.createOrganization  (new users)
     *  - ZitadelAuthenticationSuccessHandler   (returning users)
     * Dormant until a producer (#160/#161) starts populating the buffer.
     */
    fun consumePendingAvatarBytes(email: String): PendingAvatarBytes? = pendingAvatarBytes.remove(email)

    // ── Profile ───────────────────────────────────────────────────────────────

    fun updateDisplayName(userId: String, givenName: String, familyName: String, email: String) {
        idpUserDirectory.updateProfile(userId, givenName, familyName)
        memberRepository.updateProfile(userId, email, givenName, familyName)
    }
}
