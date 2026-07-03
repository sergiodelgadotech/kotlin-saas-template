package tech.sergiodelgado.saastemplate.account

/**
 * Source of a stored avatar image.
 * When deciding whether to overwrite an existing stored image, UPLOAD takes precedence over IDP:
 * a user-chosen photo is never silently replaced by an IDP re-sync.
 */
enum class AvatarSource { IDP, UPLOAD }

/**
 * Abstraction for binary avatar storage. The backing implementation (Postgres BYTEA today,
 * object storage in a future follow-up) is hidden behind this interface so callers are
 * insulated from the migration.
 *
 * Keys are [externalUserId] scoped to the current tenant (from TenantContext), so two orgs
 * with the same IdP user each get an independent stored image.
 */
interface AvatarStore {
    fun put(externalUserId: String, contentType: String, bytes: ByteArray, source: AvatarSource)
    fun get(externalUserId: String): AvatarImage?
    fun delete(externalUserId: String)
}
