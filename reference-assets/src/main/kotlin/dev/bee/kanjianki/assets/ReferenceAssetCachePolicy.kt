package dev.bee.kanjianki.assets

/**
 * Decides whether a cached reference asset must be re-extracted from the
 * packaged copy. Pure policy, no I/O: the platform loader supplies the observed
 * cache state and applies the decision.
 */
object ReferenceAssetCachePolicy {
    /** What the loader currently sees for one asset's cache slot. */
    data class CacheState(
        val present: Boolean,
        val formatVersion: Int?,
        val recordedSha256: String?,
    )

    enum class Decision {
        /** No usable cache entry: extract from the packaged asset. */
        EXTRACT,

        /** Cache entry is stale (missing/old format or hash mismatch): re-extract. */
        UPGRADE,

        /** Cache entry matches the manifest: reuse it. */
        REUSE,
    }

    /**
     * A missing or unreadable cache entry extracts; a format-version bump or a
     * recorded-hash mismatch (for a real, non-placeholder asset) upgrades;
     * otherwise the cache is reused. A placeholder asset only checks presence
     * and format version, since its content hash is intentionally unpinned.
     */
    fun decide(asset: ReferenceAsset, state: CacheState): Decision {
        if (!state.present || state.formatVersion == null) {
            return Decision.EXTRACT
        }
        if (state.formatVersion != asset.formatVersion) {
            return Decision.UPGRADE
        }
        if (!asset.hasPlaceholderHash() && state.recordedSha256 != asset.expectedSha256) {
            return Decision.UPGRADE
        }
        return Decision.REUSE
    }
}
