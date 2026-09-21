package dev.bee.kanjianki.updatecore

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies a [ReleaseManifest]'s detached Ed25519 signature over its canonical bytes.
 *
 * The trust root of the desktop update path (Goal 202): a manifest is trusted only if
 * its signature verifies under a public key the build ships and the manifest's keyId
 * names. A co-hosted checksum file proves nothing here — this is the check that a
 * failed verification never opens a package.
 *
 * A rotation release trusts more than one key: [trustedKeys] maps keyId → public key,
 * so a manifest signed by the old key still verifies while a later release removes it.
 * A manifest naming a key not in the set, or whose signature does not verify, is
 * rejected — there is no path that installs from an unverified or unknown-key manifest.
 *
 * Ed25519 is the JDK's own (`Signature("Ed25519")`, Java 15+); no third-party crypto.
 */
object ReleaseManifestVerifier {
    private const val ALGORITHM = "Ed25519"

    sealed interface Result {
        data class Verified(val manifest: ReleaseManifest) : Result
        /** [reason] is diagnostic, never shown as trust — a rejection is a rejection. */
        data class Rejected(val reason: String) : Result
    }

    /**
     * Parses [manifestBytes], then verifies [signature] against them under the key the
     * manifest names.
     *
     * [trustedKeys] are X.509-encoded Ed25519 public keys by key id. Any failure —
     * malformed manifest, unknown key, bad key bytes, or a signature that does not
     * verify — is [Result.Rejected], never a throw the caller might treat as transient.
     */
    fun verify(
        manifestBytes: ByteArray,
        signature: ByteArray,
        trustedKeys: Map<String, ByteArray>,
    ): Result {
        val manifest = try {
            ReleaseManifestCodec.parse(manifestBytes)
        } catch (failure: IllegalArgumentException) {
            return Result.Rejected("malformed manifest: ${failure.message}")
        }

        val encodedKey = trustedKeys[manifest.keyId]
            ?: return Result.Rejected("manifest signed by an untrusted key id: ${manifest.keyId}")

        val publicKey = try {
            decodeKey(encodedKey)
        } catch (failure: GeneralSecurityException) {
            return Result.Rejected("trusted key ${manifest.keyId} is not a valid Ed25519 key")
        }

        val verified = try {
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(publicKey)
            // Sign/verify the canonical bytes, not the received bytes: the manifest is
            // re-serialized so a non-canonical-but-parseable input cannot carry a
            // signature over bytes that differ from what a fresh serialization produces.
            verifier.update(ReleaseManifestCodec.canonicalBytes(manifest))
            verifier.verify(signature)
        } catch (failure: GeneralSecurityException) {
            return Result.Rejected("signature verification failed: ${failure.message}")
        }

        return if (verified) Result.Verified(manifest) else Result.Rejected("signature does not match")
    }

    private fun decodeKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(encoded))
}
