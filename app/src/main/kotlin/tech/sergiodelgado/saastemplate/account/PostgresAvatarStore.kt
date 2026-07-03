package tech.sergiodelgado.saastemplate.account

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tech.sergiodelgado.saasstarter.tenant.TenantContext
import java.time.Instant
import java.util.UUID

// ── Entity ────────────────────────────────────────────────────────────────────

/**
 * A stored binary avatar image for an org member.
 *
 * Kept in its own table (not a members column) so BYTEA blobs don't bloat every
 * member query; Spring Data JDBC would load them eagerly if they were columns.
 *
 * Implements [Persistable] following the same pattern as [tech.sergiodelgado.saasstarter.organization.Member]:
 * Spring Data JDBC treats a non-null @Id as "existing" (UPDATE) by default, so we carry a
 * [@Transient] `_new` flag that starts true (INSERT on first save) and is flipped to false by
 * [tech.sergiodelgado.saastemplate.config.AvatarStoreConfig.avatarImageAfterConvertCallback]
 * after every DB load. The update path in [PostgresAvatarStore.put] must also flip `_new = false`
 * on any copy of a loaded entity before calling save().
 *
 * Note: data class equality on ByteArray uses referential identity, which is fine
 * for persistence purposes. Avoid using == on two AvatarImage instances for content
 * equality — compare bytes.contentEquals(other.bytes) instead.
 */
@Table("avatar_image")
data class AvatarImage(
    @Id @get:JvmName("entityId") val id: UUID = UUID.randomUUID(),
    val organizationId: UUID,
    val externalUserId: String,
    val contentType: String,
    val bytes: ByteArray,
    /** AvatarSource.name — stored as VARCHAR to survive enum renames gracefully. */
    val source: String,
    val updatedAt: Instant = Instant.now(),
) : Persistable<UUID> {
    /**
     * See [tech.sergiodelgado.saasstarter.organization.Organization._new] for the full pattern.
     * Starts true so fresh instances are INSERTed; flipped to false after DB loads.
     */
    @Transient
    @JvmField
    internal var _new: Boolean = true

    override fun getId(): UUID = id
    override fun isNew(): Boolean = _new

    // ByteArray equality is referential in data classes; provide content-based overrides.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AvatarImage) return false
        return id == other.id &&
            organizationId == other.organizationId &&
            externalUserId == other.externalUserId &&
            contentType == other.contentType &&
            bytes.contentEquals(other.bytes) &&
            source == other.source &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + organizationId.hashCode()
        result = 31 * result + externalUserId.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

// ── Repository ────────────────────────────────────────────────────────────────

interface AvatarImageRepository : CrudRepository<AvatarImage, UUID> {
    fun findByOrganizationIdAndExternalUserId(organizationId: UUID, externalUserId: String): AvatarImage?
    fun deleteByOrganizationIdAndExternalUserId(organizationId: UUID, externalUserId: String)
}

// ── Implementation ────────────────────────────────────────────────────────────

/**
 * Postgres BYTEA-backed [AvatarStore].
 *
 * Tenant scoping is derived from [TenantContext] internally (matching the pattern
 * used by every other repository in the app) so callers only pass the user key.
 *
 * Source precedence: if an existing row has [AvatarSource.UPLOAD] and the incoming
 * [source] is [AvatarSource.IDP], the write is a no-op — IDP re-sync must never
 * silently clobber a user-chosen photo.
 */
@Component
@Transactional
class PostgresAvatarStore(
    private val avatarImageRepository: AvatarImageRepository,
) : AvatarStore {

    override fun put(externalUserId: String, contentType: String, bytes: ByteArray, source: AvatarSource) {
        val orgId = TenantContext.get()
        val existing = avatarImageRepository.findByOrganizationIdAndExternalUserId(orgId, externalUserId)

        // UPLOAD > IDP: never clobber a user-uploaded photo with an IDP re-sync.
        if (existing != null && existing.source == AvatarSource.UPLOAD.name && source == AvatarSource.IDP) {
            return
        }

        val toSave = if (existing != null) {
            // Reuse the existing row's id. .copy() resets _new to true (the field default),
            // so we must flip it back to false to trigger UPDATE rather than INSERT.
            existing.copy(
                contentType = contentType,
                bytes = bytes,
                source = source.name,
                updatedAt = Instant.now(),
            ).also { it._new = false }
        } else {
            AvatarImage(
                organizationId = orgId,
                externalUserId = externalUserId,
                contentType = contentType,
                bytes = bytes,
                source = source.name,
            )
        }
        avatarImageRepository.save(toSave)
    }

    @Transactional(readOnly = true)
    override fun get(externalUserId: String): AvatarImage? =
        avatarImageRepository.findByOrganizationIdAndExternalUserId(TenantContext.get(), externalUserId)

    override fun delete(externalUserId: String) {
        avatarImageRepository.deleteByOrganizationIdAndExternalUserId(TenantContext.get(), externalUserId)
    }
}
