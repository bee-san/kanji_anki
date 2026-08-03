package dev.bee.kanjianki.updatecore

import java.nio.charset.StandardCharsets

/**
 * The canonical byte serialization of a [ReleaseManifest] (Goal 202).
 *
 * Verification signs and checks these exact bytes, so they must be reproducible from
 * the manifest forever and independent of who serialized them: UTF-8, a fixed field
 * order, LF line endings, assets sorted by filename, and no wall-clock field. The
 * format is a flat `key:value` line set — deliberately not a general JSON encoder,
 * whose key ordering and whitespace are an implementation detail that could drift the
 * signed bytes between library versions.
 *
 * [parse] is strict: an unknown schema version, a missing field, a bad number, or a
 * duplicate/misordered asset is rejected rather than coerced, because a manifest that
 * parses loosely is a manifest an attacker can shape.
 */
object ReleaseManifestCodec {
    private const val LF = "\n"

    /** The canonical bytes to sign and verify. */
    fun canonicalBytes(manifest: ReleaseManifest): ByteArray =
        canonicalText(manifest).toByteArray(StandardCharsets.UTF_8)

    /** The canonical text; exposed for readable assertions and hashing. */
    fun canonicalText(manifest: ReleaseManifest): String {
        val lines = mutableListOf(
            "schemaVersion:${manifest.schemaVersion}",
            "releaseTag:${manifest.releaseTag}",
            "semanticVersion:${manifest.semanticVersion}",
            "buildSha:${manifest.buildSha}",
            "keyId:${manifest.keyId}",
            "assetCount:${manifest.assets.size}",
        )
        // Assets in a fixed order (by filename) so the same set always serializes the
        // same way regardless of the order they were assembled in.
        for (asset in manifest.assets.sortedBy { it.filename }) {
            lines += listOf(
                "asset:${asset.filename}",
                "size:${asset.sizeBytes}",
                "sha256:${asset.sha256}",
                "os:${asset.os}",
                "arch:${asset.arch}",
                "packageType:${asset.packageType}",
            )
        }
        // A trailing LF so the last line is terminated like every other; a signer that
        // omitted it and a verifier that added it would disagree on the bytes.
        return lines.joinToString(LF) + LF
    }

    /** Parses canonical bytes back into a manifest, or throws on any malformed input. */
    fun parse(bytes: ByteArray): ReleaseManifest {
        val text = String(bytes, StandardCharsets.UTF_8)
        val fields = ArrayDeque(text.split(LF).filter { it.isNotEmpty() })

        fun take(key: String): String {
            val line = fields.removeFirstOrNull() ?: throw IllegalArgumentException("manifest ended before $key")
            val prefix = "$key:"
            require(line.startsWith(prefix)) { "expected $key, got: $line" }
            return line.substring(prefix.length)
        }

        val schemaVersion = take("schemaVersion").toIntStrict("schemaVersion")
        require(schemaVersion == ReleaseManifest.CURRENT_SCHEMA_VERSION) {
            "unsupported manifest schema version: $schemaVersion"
        }
        val releaseTag = take("releaseTag")
        val semanticVersion = take("semanticVersion")
        val buildSha = take("buildSha")
        val keyId = take("keyId")
        val assetCount = take("assetCount").toIntStrict("assetCount")
        require(assetCount >= 0) { "asset count must not be negative" }

        val assets = ArrayList<ManifestAsset>(assetCount)
        repeat(assetCount) {
            assets += ManifestAsset(
                filename = take("asset"),
                sizeBytes = take("size").toLongStrict("size"),
                sha256 = take("sha256"),
                os = take("os"),
                arch = take("arch"),
                packageType = take("packageType"),
            )
        }
        require(fields.isEmpty()) { "unexpected trailing content in manifest" }
        // The parsed assets must already be in canonical (filename) order, so a
        // re-serialization is byte-identical — a reordered manifest is rejected rather
        // than silently re-sorted into a different signed document.
        require(assets == assets.sortedBy { it.filename }) { "manifest assets are not in canonical order" }
        require(assets.map { it.filename }.toSet().size == assets.size) { "duplicate asset filename" }

        return ReleaseManifest(schemaVersion, releaseTag, semanticVersion, buildSha, keyId, assets)
    }

    private fun String.toIntStrict(field: String): Int =
        toIntOrNull() ?: throw IllegalArgumentException("$field is not an integer: $this")

    private fun String.toLongStrict(field: String): Long =
        toLongOrNull() ?: throw IllegalArgumentException("$field is not a long: $this")
}
