package tech.sergiodelgado.saastemplate.organization

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tech.sergiodelgado.saasstarter.auth.idp.IdpUserDirectory
import tech.sergiodelgado.saasstarter.organization.DefaultMemberRole
import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.organization.OrganizationService
import tech.sergiodelgado.saasstarter.organization.InviteMemberCommand
import tech.sergiodelgado.saasstarter.organization.OrganizationValidations
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import tech.sergiodelgado.saasstarter.validation.validateOrThrow
import tech.sergiodelgado.saasstarter.web.ForbiddenException
import java.util.Optional

@Service
@Transactional
class MemberInvitationService(
    private val organizationService: OrganizationService,
    private val memberRepository: MemberRepository,
    private val idpUserDirectory: Optional<IdpUserDirectory>,
) {

    fun invite(email: String, role: String) {
        // 1. Auth check — caller must be OWNER or ADMIN
        val callerUserId = SecurityContextHolder.getContext().authentication?.name
            ?: error("No authenticated user in security context")
        val orgId = TenantContext.get()
        val callerMember = memberRepository.findByExternalUserId(callerUserId)
            ?.takeIf { it.organizationId == orgId }
            ?: throw ForbiddenException("Only owners and admins can invite members")
        if (callerMember.role !in setOf(DefaultMemberRole.OWNER.name, DefaultMemberRole.ADMIN.name)) {
            throw ForbiddenException("Only owners and admins can invite members")
        }

        // 2. Validate email and role
        OrganizationValidations.inviteMember.validateOrThrow(InviteMemberCommand(email, role))

        // 3. IdP lookup / create
        val directory = idpUserDirectory.orElseThrow {
            IllegalStateException(
                "IdpUserDirectory is not configured. " +
                    "Ensure a Zitadel PAT (saas-starter.zitadel.pat) is set so that " +
                    "the application can look up or provision users in the IdP."
            )
        }
        // Note: if the DB write in step 4 fails after Zitadel user creation, the invited
        // user will have received an invitation email but won't be a member yet. On retry,
        // findOrInvite will find the existing Zitadel user (no duplicate invite sent), and
        // the DB write will succeed. This is acceptable eventual-consistency across two
        // external services.
        val sub = directory.findOrInvite(email)

        // 4. Insert member
        organizationService.inviteMember(sub, role)
    }
}
