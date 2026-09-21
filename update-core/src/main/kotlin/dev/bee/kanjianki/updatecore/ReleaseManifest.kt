package dev.bee.kanjianki.updatecore

/**
 * A signed desktop release manifest (Goal 202).
 *
 * Binds, in one signed document, everything a host must trust before installing: the
 * schema version, the release tag and semantic version, the exact build SHA, the
 * signing key id, and every asset's filename, byte size, SHA-256, OS, architecture, and
 * package type. A co-hosted `SHA256SUMS.txt` alone is insufficient — anyone who can
 * replace an asset can replace that file too — so verification is against this
 * manifest's Ed25519 signature over its canonical bytes ([ReleaseManifestCodec]).
 *
 * A pure data model with no wall-clock field, deliberately: the canonical bytes must be
 * reproducible from the released artifact forever, so nothing time-varying may enter.
 */
data class ReleaseManifest(
    val schemaVersion: Int,
    val releaseTag: String,
    val semanticVersion: String,
    val buildSha: String,
    val keyId: String,
    val assets: List<ManifestAsset>,
) {
    init {
        require(schemaVersion > 0) { "schema version must be positive" }
        require(releaseTag.isNotBlank()) { "release tag must not be blank" }
        require(semanticVersion.isNotBlank()) { "semantic version must not be blank" }
        require(buildSha.isNotBlank()) { "build sha must not be blank" }
        require(keyId.isNotBlank()) { "key id must not be blank" }
    }

    companion object {
        /** The only schema version this build understands. */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/** One asset in a [ReleaseManifest]: its identity, integrity, and platform. */
data class ManifestAsset(
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val os: String,
    val arch: String,
    val packageType: String,
) {
    init {
        require(filename.isNotBlank()) { "asset filename must not be blank" }
        require(sizeBytes > 0L) { "asset size must be positive" }
        require(SHA256_HEX.matches(sha256)) { "asset sha256 must be 64 lowercase hex chars" }
        require(os.isNotBlank()) { "asset os must not be blank" }
        require(arch.isNotBlank()) { "asset arch must not be blank" }
        require(packageType.isNotBlank()) { "asset package type must not be blank" }
    }
}
