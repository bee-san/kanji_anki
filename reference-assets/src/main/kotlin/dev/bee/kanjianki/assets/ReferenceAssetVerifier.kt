package dev.bee.kanjianki.assets

import java.io.InputStream
import java.security.MessageDigest

/** Streaming SHA-256 verification of reference-asset bytes. */
object ReferenceAssetVerifier {
    private const val BUFFER_BYTES = 64 * 1024

    /**
     * Streams [input] fully, returning its lowercase-hex SHA-256. The caller
     * owns [input] and must close it; this never buffers the whole asset in
     * memory (dictionary databases can be multiple MiB).
     */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            total += read
        }
        require(total > 0L) { "reference asset stream was empty" }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * Verifies streamed bytes against [asset]'s expected hash. A placeholder
     * hash accepts any non-empty content (the binary is not sourced yet); a
     * real hash must match exactly. Returns the observed hash so callers can
     * record it.
     */
    fun verify(asset: ReferenceAsset, input: InputStream): VerificationResult {
        val observed = sha256(input)
        val accepted = asset.hasPlaceholderHash() || observed == asset.expectedSha256
        return VerificationResult(
            assetId = asset.id,
            observedSha256 = observed,
            accepted = accepted,
            placeholder = asset.hasPlaceholderHash(),
        )
    }

    data class VerificationResult(
        val assetId: String,
        val observedSha256: String,
        val accepted: Boolean,
        val placeholder: Boolean,
    )
}
