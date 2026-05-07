package org.granchi.mvpsaas.organization

import org.granchi.saasstarter.lock.RedisLockService
import org.granchi.saasstarter.tenant.TenantContext
import org.granchi.saasstarter.web.NotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val memberRepository: MemberRepository,
    private val lockService: RedisLockService
) {
    fun current(): Organization =
        organizationRepository.findById(TenantContext.get()).orElseThrow {
            NotFoundException("Organization not found")
        }

    fun members(): List<Member> =
        memberRepository.findByOrganizationId(TenantContext.get())

    fun inviteMember(zitadelUserId: String, role: Member.Role = Member.Role.MEMBER): Member {
        OrganizationValidations.inviteMember.validate(InviteMemberCommand(zitadelUserId, role))
        val orgId = TenantContext.get()
        return lockService.withLock("invite:$orgId:$zitadelUserId") {
            check(!memberRepository.existsByOrganizationIdAndClerkUserId(orgId, zitadelUserId)) {
                "User is already a member of this organization"
            }
            memberRepository.save(
                Member(organizationId = orgId, zitadelUserId = zitadelUserId, role = role)
            )
        }
    }

    // Evict cache when member is removed — next request re-resolves tenant
    @CacheEvict("tenant-by-user", key = "#result.zitadelUserId", condition = "#result != null")
    fun removeMember(memberId: java.util.UUID): Member {
        val member = memberRepository.findById(memberId).orElseThrow {
            NotFoundException("Member not found")
        }
        check(member.organizationId == TenantContext.get()) {
            "Member does not belong to current organization"
        }
        memberRepository.delete(member)
        return member
    }

    fun updateName(name: String): Organization {
        OrganizationValidations.updateOrganization.validate(UpdateOrganizationCommand(name))
        val org = current()
        return organizationRepository.save(org.copy(name = name))
    }
}
