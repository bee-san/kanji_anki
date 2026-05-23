package dev.bee.kanjianki.updatecore

import java.util.Locale

object Sha256Digest {
    private const val SHA256_HEX_LENGTH = 64
    private val SHA256_PATTERN = Regex("(?i)\\b([a-f0-9]{$SHA256_HEX_LENGTH})\\b")
    private val SHA256_DIGEST_PATTERN = Regex("(?i)[0-9a-f]{$SHA256_HEX_LENGTH}")

    @JvmStatic
    fun findInText(checksumText: String?): String {
        if (checksumText == null) {
            return ""
        }
        val match = SHA256_PATTERN.find(checksumText)
        return match?.groupValues?.get(1)?.let(::normalize).orEmpty()
    }

    @JvmStatic
    fun isDigest(expected: String?): Boolean {
        return expected != null && SHA256_DIGEST_PATTERN.matches(expected.trim())
    }

    private fun normalize(digest: String): String {
        return digest.lowercase(Locale.ROOT)
    }
}
