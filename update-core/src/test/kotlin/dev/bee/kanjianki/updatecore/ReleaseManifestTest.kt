package dev.bee.kanjianki.updatecore

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseManifestTest {
    @Test
    fun canonicalBytesAreStableRegardlessOfAssetOrder() {
        val a = asset("kani-desktop-linux-x64-1.0.0.deb")
        val b = asset("kani-desktop-windows-x64-1.0.0.msi")
        val one = manifest(listOf(a, b))
        val other = manifest(listOf(b, a))

        assertEquals(
            ReleaseManifestCodec.canonicalText(one),
            ReleaseManifestCodec.canonicalText(other),
        )
        // No wall-clock field leaked in.
        assertTrue(!ReleaseManifestCodec.canonicalText(one).contains("time"))
    }

    @Test
    fun parseIsTheInverseOfSerializeForACanonicalManifest() {
        val original = manifest(listOf(asset("kani-desktop-macos-arm64-1.0.0.dmg")))
        val roundTripped = ReleaseManifestCodec.parse(ReleaseManifestCodec.canonicalBytes(original))
        assertEquals(original, roundTripped)
    }

    @Test
    fun parseRejectsAnUnknownSchemaMissingFieldOrBadNumber() {
        val good = ReleaseManifestCodec.canonicalText(manifest(listOf(asset("x-1.0.0.deb"))))
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseManifestCodec.parse(good.replace("schemaVersion:1", "schemaVersion:999").toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseManifestCodec.parse(good.replace("size:1024\n", "").toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseManifestCodec.parse(good.replace("size:1024", "size:not-a-number").toByteArray())
        }
    }

    @Test
    fun aValidSignatureUnderATrustedKeyVerifies() {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val manifest = manifest(listOf(asset("kani-desktop-linux-x64-1.0.0.deb")))
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest)

        val result = ReleaseManifestVerifier.verify(
            manifestBytes = bytes,
            signature = sign(keys, bytes),
            trustedKeys = mapOf("kani-release-key-1" to keys.public.encoded),
        )

        assertTrue(result is ReleaseManifestVerifier.Result.Verified)
        assertEquals(manifest, (result as ReleaseManifestVerifier.Result.Verified).manifest)
    }

    @Test
    fun aTamperedManifestIsRejectedEvenWithAValidSignatureOverTheOriginal() {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val original = manifest(listOf(asset("kani-desktop-linux-x64-1.0.0.deb")))
        val signature = sign(keys, ReleaseManifestCodec.canonicalBytes(original))
        // Swap the asset's sha256 after signing: the bytes no longer match the signature.
        val tampered = ReleaseManifestCodec.canonicalText(original)
            .replace(VALID_SHA, "b".repeat(64))
            .toByteArray()

        val result = ReleaseManifestVerifier.verify(tampered, signature, mapOf("kani-release-key-1" to keys.public.encoded))
        assertTrue(result is ReleaseManifestVerifier.Result.Rejected)
    }

    @Test
    fun anUnknownKeyIdIsRejected() {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest(listOf(asset("x-1.0.0.deb"))))

        val result = ReleaseManifestVerifier.verify(
            bytes,
            sign(keys, bytes),
            trustedKeys = mapOf("some-other-key" to keys.public.encoded),
        )
        assertTrue(result is ReleaseManifestVerifier.Result.Rejected)
    }

    @Test
    fun aRotationTrustsBothOldAndNewKeys() {
        val oldKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val newKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        assertNotEquals(oldKeys.public, newKeys.public)
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest(listOf(asset("x-1.0.0.deb"))))
        val trusted = mapOf(
            "kani-release-key-1" to oldKeys.public.encoded,
            "kani-release-key-2" to newKeys.public.encoded,
        )

        // Signed by the still-trusted old key (the manifest names key 1).
        val result = ReleaseManifestVerifier.verify(bytes, sign(oldKeys, bytes), trusted)
        assertTrue(result is ReleaseManifestVerifier.Result.Verified)
    }

    @Test
    fun theManifestModelRejectsBlankOrInvalidFields() {
        assertEquals(1, ReleaseManifest.CURRENT_SCHEMA_VERSION)
        assertThrows(IllegalArgumentException::class.java) { manifest(emptyList()).copy(schemaVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { manifest(emptyList()).copy(releaseTag = "") }
        assertThrows(IllegalArgumentException::class.java) { manifest(emptyList()).copy(semanticVersion = " ") }
        assertThrows(IllegalArgumentException::class.java) { manifest(emptyList()).copy(buildSha = "") }
        assertThrows(IllegalArgumentException::class.java) { manifest(emptyList()).copy(keyId = " ") }
    }

    @Test
    fun theAssetModelRejectsBlankFieldsBadSizeOrBadDigest() {
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(filename = " ") }
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(sizeBytes = 0L) }
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(sha256 = "short") }
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(sha256 = "A".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(os = "") }
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(arch = " ") }
        assertThrows(IllegalArgumentException::class.java) { asset("x.deb").copy(packageType = "") }
    }

    @Test
    fun parseRejectsDuplicateOrMisorderedAssets() {
        val two = manifest(listOf(asset("a-1.0.0.deb"), asset("b-1.0.0.deb")))
        val canonical = ReleaseManifestCodec.canonicalText(two)
        // Swap the two asset blocks so they are no longer in filename order.
        val misordered = canonical
            .replace("asset:a-1.0.0.deb", "asset:__TMP__")
            .replace("asset:b-1.0.0.deb", "asset:a-1.0.0.deb")
            .replace("asset:__TMP__", "asset:b-1.0.0.deb")
        assertThrows(IllegalArgumentException::class.java) { ReleaseManifestCodec.parse(misordered.toByteArray()) }
    }

    @Test
    fun aMalformedManifestIsRejectedNotThrown() {
        val result = ReleaseManifestVerifier.verify(
            manifestBytes = "not a manifest".toByteArray(),
            signature = ByteArray(64),
            trustedKeys = emptyMap(),
        )
        assertTrue(result is ReleaseManifestVerifier.Result.Rejected)
    }

    private fun sign(keys: KeyPair, bytes: ByteArray): ByteArray {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keys.private)
        signer.update(bytes)
        return signer.sign()
    }

    private fun manifest(assets: List<ManifestAsset>) = ReleaseManifest(
        schemaVersion = ReleaseManifest.CURRENT_SCHEMA_VERSION,
        releaseTag = "v1.0.0",
        semanticVersion = "1.0.0",
        buildSha = "abc123",
        keyId = "kani-release-key-1",
        assets = assets,
    )

    private fun asset(filename: String) = ManifestAsset(
        filename = filename,
        sizeBytes = 1024L,
        sha256 = VALID_SHA,
        os = "linux",
        arch = "x64",
        packageType = "deb",
    )

    private companion object {
        val VALID_SHA = "a".repeat(64)
    }
}
