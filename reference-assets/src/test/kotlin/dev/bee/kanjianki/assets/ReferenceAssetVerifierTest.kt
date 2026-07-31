package dev.bee.kanjianki.assets

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAssetVerifierTest {
    @Test
    fun sha256MatchesTheKnownVector() {
        // SHA-256("abc") is a stable published vector.
        val hash = ReferenceAssetVerifier.sha256(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

    @Test
    fun anEmptyStreamIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceAssetVerifier.sha256(ByteArrayInputStream(ByteArray(0)))
        }
    }

    @Test
    fun placeholderAssetAcceptsAnyNonEmptyContent() {
        val asset = asset(ReferenceAssetManifest.PLACEHOLDER_SHA256)
        val result = ReferenceAssetVerifier.verify(asset, ByteArrayInputStream("anything".toByteArray()))
        assertTrue(result.accepted)
        assertTrue(result.placeholder)
    }

    @Test
    fun realAssetRequiresAnExactHash() {
        val content = "the-real-bytes".toByteArray()
        val expected = ReferenceAssetVerifier.sha256(ByteArrayInputStream(content))
        val matching = ReferenceAssetVerifier.verify(asset(expected), ByteArrayInputStream(content))
        assertTrue(matching.accepted)
        assertFalse(matching.placeholder)

        val mismatch = ReferenceAssetVerifier.verify(asset("b".repeat(64)), ByteArrayInputStream(content))
        assertFalse(mismatch.accepted)
    }

    @Test
    fun cachePolicyCoversExtractUpgradeAndReuse() {
        val placeholder = asset(ReferenceAssetManifest.PLACEHOLDER_SHA256)
        assertEquals(
            ReferenceAssetCachePolicy.Decision.EXTRACT,
            ReferenceAssetCachePolicy.decide(
                placeholder,
                ReferenceAssetCachePolicy.CacheState(present = false, formatVersion = null, recordedSha256 = null),
            ),
        )
        assertEquals(
            ReferenceAssetCachePolicy.Decision.UPGRADE,
            ReferenceAssetCachePolicy.decide(
                placeholder,
                ReferenceAssetCachePolicy.CacheState(present = true, formatVersion = placeholder.formatVersion - 1, recordedSha256 = "x"),
            ),
        )
        assertEquals(
            ReferenceAssetCachePolicy.Decision.REUSE,
            ReferenceAssetCachePolicy.decide(
                placeholder,
                ReferenceAssetCachePolicy.CacheState(present = true, formatVersion = placeholder.formatVersion, recordedSha256 = "x"),
            ),
        )

        val real = asset("c".repeat(64))
        assertEquals(
            "a recorded-hash mismatch upgrades a real asset",
            ReferenceAssetCachePolicy.Decision.UPGRADE,
            ReferenceAssetCachePolicy.decide(
                real,
                ReferenceAssetCachePolicy.CacheState(present = true, formatVersion = real.formatVersion, recordedSha256 = "d".repeat(64)),
            ),
        )
        assertEquals(
            ReferenceAssetCachePolicy.Decision.REUSE,
            ReferenceAssetCachePolicy.decide(
                real,
                ReferenceAssetCachePolicy.CacheState(present = true, formatVersion = real.formatVersion, recordedSha256 = "c".repeat(64)),
            ),
        )
    }

    private fun asset(sha: String) = ReferenceAsset(
        id = "x",
        kind = ReferenceAssetKind.DICTIONARY_DATABASE,
        fileName = "x.db",
        expectedSha256 = sha,
        formatVersion = 3,
        extractionTarget = "reference/x.db",
        license = ReferenceAssetLicense("n", "spdx", null, "attr"),
    )
}
