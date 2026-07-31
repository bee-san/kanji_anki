package dev.bee.kanjianki.backup.core

/**
 * The backward-compatible portable metadata carried inside a backup's own
 * `settings` table, shared by every host. It records the backup's origin
 * platform, the portable format version, and the schema version so a
 * destination can decide how to restore it. Backups written before this
 * metadata existed are valid but classify as [Origin.UNKNOWN] and take the
 * unknown-origin revalidation path (see [CrossPlatformRestorePlanner]).
 *
 * The values live under reserved `settings` keys so they travel with the
 * database itself and survive any host's snapshot/restore unchanged. This class
 * holds only the encode/decode of those key-value rows; reading and writing the
 * rows stays in platform code.
 */
object PortableBackupMetadata {
    const val ORIGIN_KEY = "portable_backup_origin"
    const val FORMAT_VERSION_KEY = "portable_backup_format_version"
    const val SCHEMA_VERSION_KEY = "portable_backup_schema_version"

    /** Current portable format version. Bump only on a breaking format change. */
    const val CURRENT_FORMAT_VERSION = 1

    enum class Origin(val wireName: String) {
        ANDROID("android"),
        DESKTOP("desktop"),
        UNKNOWN("unknown"),
        ;

        companion object {
            fun fromWire(value: String?): Origin =
                entries.firstOrNull { it.wireName == value?.trim()?.lowercase() } ?: UNKNOWN
        }
    }

    data class Metadata(
        val origin: Origin,
        val formatVersion: Int,
        val schemaVersion: Int,
    )

    /** The reserved settings keys, so callers can exclude them from user-facing views. */
    val reservedKeys: Set<String> = linkedSetOf(ORIGIN_KEY, FORMAT_VERSION_KEY, SCHEMA_VERSION_KEY)

    /** The settings rows to stamp into a backup for [origin] at [schemaVersion]. */
    fun rowsFor(origin: Origin, schemaVersion: Int): Map<String, String> = linkedMapOf(
        ORIGIN_KEY to origin.wireName,
        FORMAT_VERSION_KEY to CURRENT_FORMAT_VERSION.toString(),
        SCHEMA_VERSION_KEY to schemaVersion.toString(),
    )

    /**
     * Decodes metadata from a backup's settings rows. Missing origin →
     * [Origin.UNKNOWN]; missing/malformed versions → 0, which forces the
     * unknown-origin revalidation path. Never throws.
     */
    fun decode(settings: Map<String, String>): Metadata = Metadata(
        origin = Origin.fromWire(settings[ORIGIN_KEY]),
        formatVersion = settings[FORMAT_VERSION_KEY]?.trim()?.toIntOrNull() ?: 0,
        schemaVersion = settings[SCHEMA_VERSION_KEY]?.trim()?.toIntOrNull() ?: 0,
    )

    /**
     * Maps a decoded [Origin] to a [CrossPlatformRestorePlanner.Host] so the
     * restore planner can decide reset/revalidation.
     */
    fun host(origin: Origin): CrossPlatformRestorePlanner.Host = when (origin) {
        Origin.ANDROID -> CrossPlatformRestorePlanner.Host.ANDROID
        Origin.DESKTOP -> CrossPlatformRestorePlanner.Host.DESKTOP
        Origin.UNKNOWN -> CrossPlatformRestorePlanner.Host.UNKNOWN
    }
}
