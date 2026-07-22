package fr.luteal.core.network.mapping

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Sync envelope metadata the domain models do not carry. The sync layer owns
 * these values per record: [clientRev] is a fresh UUID on every local edit
 * (the conflict tiebreak after updated_at); [createdAt]/[updatedAt] are RFC
 * 3339 UTC; [deletedAt] marks a tombstone.
 */
data class SyncMeta(
    val clientRev: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime? = null,
)

/**
 * Stable id for records derived from a parent (e.g. a bleeding observation
 * fanned out of a cycle's period day). Deterministic so the same source data
 * maps to the same id across syncs, which is what lets upserts converge
 * instead of duplicating rows.
 */
fun deterministicId(vararg parts: String): UUID =
    UUID.nameUUIDFromBytes(parts.joinToString("|").encodeToByteArray())
