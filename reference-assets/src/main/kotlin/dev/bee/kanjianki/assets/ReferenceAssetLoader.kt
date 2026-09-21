package dev.bee.kanjianki.assets

import java.io.IOException
import java.io.InputStream

/**
 * Opens the packaged (read-only) copy of a reference asset. Android backs this
 * with `AssetManager.open`; desktop with a classpath/installed-image resource.
 * Kept as an interface so `:reference-assets` stays pure-JVM and platform-free.
 */
fun interface PackagedAssetSource {
    /** Opens the packaged bytes for [asset]. The caller closes the stream. */
    @Throws(IOException::class)
    fun open(asset: ReferenceAsset): InputStream
}

/**
 * The mutable install/cache slot for extracted assets. Implementations perform
 * atomic, durable installs; this module only orchestrates the decision.
 */
interface ReferenceAssetCache {
    fun stateOf(asset: ReferenceAsset): ReferenceAssetCachePolicy.CacheState

    /**
     * Atomically installs the packaged bytes for [asset] into its extraction
     * target and records [observedSha256] and the manifest format version.
     */
    @Throws(IOException::class)
    fun install(asset: ReferenceAsset, source: PackagedAssetSource, observedSha256: String)
}

/**
 * Loads every asset in a [ReferenceAssetManifest]: for each, decides whether to
 * extract/upgrade/reuse, verifies the packaged bytes before install, and
 * reports one outcome per asset. A verification rejection or I/O error for one
 * asset is isolated so a single corrupt asset cannot block the others.
 */
class ReferenceAssetLoader(
    private val manifest: ReferenceAssetManifest,
    private val source: PackagedAssetSource,
    private val cache: ReferenceAssetCache,
) {
    fun loadAll(): List<Outcome> = manifest.assets.map(::load)

    fun load(asset: ReferenceAsset): Outcome {
        val decision = ReferenceAssetCachePolicy.decide(asset, cache.stateOf(asset))
        if (decision == ReferenceAssetCachePolicy.Decision.REUSE) {
            return Outcome(asset.id, decision, verified = false, placeholder = asset.hasPlaceholderHash(), error = null)
        }
        return try {
            val verification = source.open(asset).use { ReferenceAssetVerifier.verify(asset, it) }
            if (!verification.accepted) {
                return Outcome(
                    asset.id,
                    decision,
                    verified = false,
                    placeholder = verification.placeholder,
                    error = "sha256 mismatch: expected ${asset.expectedSha256}, got ${verification.observedSha256}",
                )
            }
            cache.install(asset, source, verification.observedSha256)
            Outcome(asset.id, decision, verified = true, placeholder = verification.placeholder, error = null)
        } catch (io: IOException) {
            Outcome(asset.id, decision, verified = false, placeholder = asset.hasPlaceholderHash(), error = io.message ?: "io error")
        }
    }

    data class Outcome(
        val assetId: String,
        val decision: ReferenceAssetCachePolicy.Decision,
        val verified: Boolean,
        val placeholder: Boolean,
        val error: String?,
    ) {
        fun succeeded(): Boolean = error == null
    }
}
