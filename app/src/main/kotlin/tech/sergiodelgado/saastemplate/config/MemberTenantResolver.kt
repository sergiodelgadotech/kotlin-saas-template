package tech.sergiodelgado.saastemplate.config

import tech.sergiodelgado.saasstarter.organization.MemberRepository
import tech.sergiodelgado.saasstarter.tenant.TenantResolver
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Implementation of the starter's TenantResolver for this app.
 *
 * The starter library doesn't know we use Zitadel and store members
 * in a `members` table — that's our domain decision. This class
 * bridges the library's interface with our concrete repository.
 */
@Component
class MemberTenantResolver(
    private val memberRepository: MemberRepository
) : TenantResolver {

    override fun resolveTenantId(userId: String): UUID? =
        memberRepository.findOrganizationIdByUserId(userId)?.let { UUID.fromString(it) }
}
