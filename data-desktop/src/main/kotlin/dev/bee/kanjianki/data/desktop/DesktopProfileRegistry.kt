package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.core.KaniJson

/**
 * The opaque local profile registry that lives OUTSIDE any portable database.
 * It records only selection/display state — never Kani study data, and never
 * the opaque provider binding (that lives inside each profile's portable
 * database so a backup transfer can validate it).
 *
 * One entry per profile: its UUID directory name and a user-facing display
 * name. A default profile preserves the single-profile first-run experience.
 * This holds the pure model + JSON codec; reading/writing the registry file and
 * creating profile directories are platform I/O concerns.
 */
data class DesktopProfileRegistry(
    val profiles: List<DesktopProfileEntry>,
    val selectedProfileId: String?,
) {
    init {
        require(profiles.map(DesktopProfileEntry::id).toSet().size == profiles.size) {
            "duplicate profile id in registry"
        }
        require(selectedProfileId == null || profiles.any { it.id == selectedProfileId }) {
            "selected profile id is not in the registry"
        }
    }

    fun selected(): DesktopProfileEntry? = profiles.firstOrNull { it.id == selectedProfileId }

    /** Adds a profile (or updates its display name) and selects it. */
    fun withProfile(entry: DesktopProfileEntry): DesktopProfileRegistry {
        val others = profiles.filterNot { it.id == entry.id }
        return DesktopProfileRegistry(others + entry, entry.id)
    }

    /** Removes a profile; re-selects the first remaining profile, if any. */
    fun withoutProfile(profileId: String): DesktopProfileRegistry {
        val remaining = profiles.filterNot { it.id == profileId }
        val nextSelected = when {
            remaining.isEmpty() -> null
            selectedProfileId == profileId -> remaining.first().id
            else -> selectedProfileId
        }
        return DesktopProfileRegistry(remaining, nextSelected)
    }

    fun select(profileId: String): DesktopProfileRegistry {
        require(profiles.any { it.id == profileId }) { "cannot select an unknown profile" }
        return copy(selectedProfileId = profileId)
    }

    fun encode(): String =
        KaniJson.encode(
            linkedMapOf(
                "version" to VERSION.toLong(),
                "selectedProfileId" to selectedProfileId,
                "profiles" to profiles.map {
                    linkedMapOf<String, Any?>("id" to it.id, "displayName" to it.displayName)
                },
            ),
        )

    companion object {
        const val VERSION = 1
        const val DEFAULT_DISPLAY_NAME = "Default"

        fun empty(): DesktopProfileRegistry = DesktopProfileRegistry(emptyList(), null)

        /**
         * A fresh registry with one default profile selected, preserving the
         * single-profile first-run experience.
         */
        fun withDefault(profileId: String): DesktopProfileRegistry {
            require(DesktopStorageLayout.isValidProfileId(profileId)) { "invalid default profile id" }
            return DesktopProfileRegistry(
                listOf(DesktopProfileEntry(profileId, DEFAULT_DISPLAY_NAME)),
                profileId,
            )
        }

        /** Decodes a registry, falling back to [empty] on malformed input. */
        fun decode(encoded: String?): DesktopProfileRegistry {
            val root = KaniJson.decode(encoded) ?: return empty()
            val rawProfiles = (root["profiles"] as? List<*>).orEmpty()
            val profiles = rawProfiles.mapNotNull { raw ->
                @Suppress("UNCHECKED_CAST")
                val obj = raw as? Map<String, Any?> ?: return@mapNotNull null
                val id = obj["id"] as? String ?: return@mapNotNull null
                if (!DesktopStorageLayout.isValidProfileId(id)) return@mapNotNull null
                DesktopProfileEntry(id, (obj["displayName"] as? String)?.ifBlank { DEFAULT_DISPLAY_NAME } ?: DEFAULT_DISPLAY_NAME)
            }.distinctBy(DesktopProfileEntry::id)
            val selected = (root["selectedProfileId"] as? String)?.takeIf { sel -> profiles.any { it.id == sel } }
            return DesktopProfileRegistry(profiles, selected)
        }
    }
}

data class DesktopProfileEntry(
    val id: String,
    val displayName: String,
) {
    init {
        require(DesktopStorageLayout.isValidProfileId(id)) { "invalid profile id: $id" }
        require(displayName.isNotBlank()) { "profile display name must not be blank" }
    }
}
