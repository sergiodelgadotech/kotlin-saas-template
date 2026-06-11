package tech.sergiodelgado.saastemplate.organization

import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tech.sergiodelgado.saasstarter.organization.DefaultMemberRole
import tech.sergiodelgado.saasstarter.organization.Member
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.Organization
import tech.sergiodelgado.saasstarter.organization.OrganizationRepository
import kotlin.random.Random

@Service
@Transactional
class OnboardingService(
    private val organizationRepository: OrganizationRepository,
    private val memberRepository: MemberRepository,
) {
    // Evict the cached null that ZitadelAuthenticationSuccessHandler wrote during login
    // (MemberRepository.findOrganizationIdByUserId is @Cacheable and caches null).
    @CacheEvict(cacheNames = ["tenant-by-user"], key = "#ownerUserId")
    fun createOrganization(
        ownerUserId: String,
        name: String,
        email: String = "",
        firstName: String? = null,
        lastName: String? = null,
    ): Organization {
        val org = organizationRepository.save(Organization(name = name, slug = slugFor(name)))
        memberRepository.save(
            Member(
                organizationId = org.id,
                externalUserId = ownerUserId,
                role = DefaultMemberRole.OWNER.name,
                email = email,
                firstName = firstName,
                lastName = lastName,
            )
        )
        return org
    }

    private fun slugFor(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        val suffix = (1..6).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
        return "$base-$suffix"
    }
}
