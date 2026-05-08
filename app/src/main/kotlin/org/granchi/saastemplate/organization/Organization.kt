package org.granchi.saastemplate.organization

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("organizations")
data class Organization(
    @Id val id: UUID = UUID.randomUUID(),
    val name: String,
    val slug: String,
    val plan: String = "starter",
    val createdAt: Instant = Instant.now()
)

@Table("members")
data class Member(
    @Id val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    val zitadelUserId: String,
    val role: Role = Role.MEMBER,
    val createdAt: Instant = Instant.now()
) {
    enum class Role { OWNER, ADMIN, MEMBER }
}
