package org.granchi.saastemplate.organization

import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface OrganizationRepository : CrudRepository<Organization, UUID> {
    fun findBySlug(slug: String): Organization?
}

interface MemberRepository : CrudRepository<Member, UUID> {
    fun findByOrganizationId(organizationId: UUID): List<Member>
    fun findByZitadelUserId(zitadelUserId: String): Member?

    // Cached — this query runs on every single authenticated request.
    // Evict when a member is added or removed (see OrganizationService).
    @Cacheable("tenant-by-user", key = "#userId")
    @Query("SELECT organization_id FROM members WHERE zitadel_user_id = :userId")
    fun findOrganizationIdByUserId(userId: String): UUID?

    fun existsByOrganizationIdAndZitadelUserId(organizationId: UUID, zitadelUserId: String): Boolean
}
