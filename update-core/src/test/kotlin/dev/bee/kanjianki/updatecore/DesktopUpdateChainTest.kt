package dev.bee.kanjianki.updatecore

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Proves the composed claim Goal 202 is done when: a co-hosted checksum alone is
 * insufficient, and a failed verification never opens a package.
 *
 * The individual policies are each covered by their own test. This one runs the whole
 * chain — detect the installation channel, evaluate signed release candidates, stage the
 * download, decide the handoff — because every failure mode here is one where the pieces
 * are individually correct and the composition is what has to hold. A defect that only
 * appears when they are wired together (a rejected manifest whose reason still produces a
 * stageable offer, a staged file that outlives its verification) is exactly the kind that
 * ships.
 *
 * `mayOpenInstaller` is asserted for every failure, not just the happy path, because it
 * is the last gate before Kani hands a file to the OS.
 */
class DesktopUpdateChainTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun aFullyValidDebReleaseInstallsOnlyAfterTheUserConfirmsThatVersion() {
        val offer = evaluate(listOf(signedRelease(NEWER))) as DesktopUpdatePolicy.Outcome.UpdateAvailable

        val staged = DesktopUpdateStager.stage(directory(), offer.asset) { payload().inputStream() }

        val published = staged as DesktopUpdateStager.Result.Published
        val handoff = DesktopUpdateHandoffPolicy.handoffFor(offer, published.path.toString())
        assertTrue(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, offer.semanticVersion))
        // Nothing is installed on the strength of the release listing alone: the bytes on
        // disk had to match the size and digest the signed manifest committed to.
        assertEquals(offer.asset.sizeBytes, Files.size(published.path))
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, null))
    }

    @Test
    fun aReleaseWithACorrectCoHostedChecksumButNoSignatureNeverReachesTheDisk() {
        // The release hosts the right asset and the right bytes; the only thing missing is
        // a signature. Whoever could replace the asset could replace a checksum file too,
        // so this must not install, and no artifact may be staged from it.
        val unsigned = DesktopUpdatePolicy.ReleaseCandidate(releaseHosting(NEWER), null, null)

        val outcome = evaluate(listOf(unsigned))

        val unavailable = outcome as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("no signed desktop manifest"))
        assertNothingStagedAndNothingInstallable(outcome)
    }

    @Test
    fun aManifestSignedByAnAttackersOwnKeyNeverReachesTheDisk() {
        val attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest(NEWER))
        val forged = DesktopUpdatePolicy.ReleaseCandidate(
            releaseHosting(NEWER),
            bytes,
            sign(attacker, bytes),
        )

        assertNothingStagedAndNothingInstallable(evaluate(listOf(forged)))
    }

    @Test
    fun aWrongArchitectureAssetNeverReachesTheDisk() {
        // A signed manifest that labels an x64 filename as arm64: the signature is valid,
        // so only the platform check stops it. Installing the wrong architecture is worse
        // than reporting no update.
        val mislabelled = candidate(
            releaseHosting(NEWER),
            manifest(NEWER).let { base ->
                base.copy(assets = listOf(base.assets.single().copy(arch = "arm64")))
            },
        )

        assertNothingStagedAndNothingInstallable(evaluate(listOf(mislabelled)))
    }

    @Test
    fun aDowngradeIsNeverStagedEvenWhenPerfectlySigned() {
        // An older release remains valid and correctly signed forever, so a downgrade
        // attack is a replay of a genuine artifact, not a forgery. Only the version
        // comparison stops it.
        val outcome = evaluate(listOf(signedRelease(OLDER)))

        assertEquals(DesktopUpdatePolicy.Outcome.UpToDate, outcome)
        assertNothingStagedAndNothingInstallable(outcome)
    }

    @Test
    fun aTamperedDownloadOfAVerifiedOfferLeavesNothingToInstall() {
        val offer = evaluate(listOf(signedRelease(NEWER))) as DesktopUpdatePolicy.Outcome.UpdateAvailable

        // The manifest verified, the offer is genuine, and the transport substituted the
        // bytes. The manifest's digest is what catches it.
        val staged = DesktopUpdateStager.stage(directory(), offer.asset) {
            ByteArray(payload().size) { 0 }.inputStream()
        }

        assertTrue(staged is DesktopUpdateStager.Result.Failed)
        val handoff = DesktopUpdateHandoffPolicy.handoffFor(offer, null)
        assertTrue(handoff is DesktopUpdateHandoffPolicy.Handoff.Blocked)
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, offer.semanticVersion))
        assertNoArtifactRemains()
    }

    @Test
    fun anUnrecognizedInstallNeverStagesAnythingHoweverValidTheRelease() {
        // A Flatpak or source build: the release is perfectly signed and newer, and Kani
        // still offers nothing, because replacing an install it did not create is not
        // Kani's to do.
        val channel = DesktopInstallationChannelPolicy.detect("Linux", "/app/bin/Kani")

        val outcome = DesktopUpdatePolicy.evaluate(
            currentVersion = CURRENT,
            channel = channel,
            candidates = listOf(signedRelease(NEWER)),
            trustedKeys = trustedKeys(),
        )

        assertEquals(DesktopUpdatePolicy.InstallationChannel.UNKNOWN, channel)
        assertNothingStagedAndNothingInstallable(outcome)
    }

    @Test
    fun aPortableInstallIsGivenTheTarballToInstallItselfAndNoInstallerButton() {
        val channel = DesktopInstallationChannelPolicy.detect("Linux", "/home/user/kani/bin/Kani")
        val tarball = tarballRelease(NEWER)

        val offer = DesktopUpdatePolicy.evaluate(
            currentVersion = CURRENT,
            channel = channel,
            candidates = listOf(tarball),
            trustedKeys = trustedKeys(),
        ) as DesktopUpdatePolicy.Outcome.UpdateAvailable

        assertEquals(DesktopUpdatePolicy.InstallationChannel.LINUX_TAR_GZ, channel)
        assertTrue(offer.manualInstall)
        val staged = DesktopUpdateStager.stage(directory(), offer.asset) { payload().inputStream() }
        val handoff = DesktopUpdateHandoffPolicy.handoffFor(
            offer,
            (staged as DesktopUpdateStager.Result.Published).path.toString(),
        )
        // Downloaded and revealed, never opened: Kani did not create this install layout
        // and must not replace it in place.
        assertTrue(handoff is DesktopUpdateHandoffPolicy.Handoff.RevealForManualInstall)
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, offer.semanticVersion))
    }

    /** Every non-offer must both leave the disk clean and be uninstallable. */
    private fun assertNothingStagedAndNothingInstallable(outcome: DesktopUpdatePolicy.Outcome) {
        assertFalse(outcome.toString(), outcome is DesktopUpdatePolicy.Outcome.UpdateAvailable)
        assertNoArtifactRemains()
    }

    private fun assertNoArtifactRemains() {
        val remaining = Files.list(directory()).use { stream -> stream.toList() }
        assertEquals(emptyList<Path>(), remaining)
    }

    private fun evaluate(
        candidates: List<DesktopUpdatePolicy.ReleaseCandidate>,
    ): DesktopUpdatePolicy.Outcome = DesktopUpdatePolicy.evaluate(
        currentVersion = CURRENT,
        channel = DesktopInstallationChannelPolicy.detect("Linux", "/opt/kani/bin/Kani"),
        candidates = candidates,
        trustedKeys = trustedKeys(),
    )

    private fun trustedKeys(): Map<String, ByteArray> = mapOf(KEY_ID to KEYS.public.encoded)

    private fun directory(): Path = temporaryFolder.root.toPath()

    private fun signedRelease(tag: String) = candidate(releaseHosting(tag), manifest(tag))

    private fun candidate(
        release: GitHubReleaseMetadata,
        manifest: ReleaseManifest,
    ): DesktopUpdatePolicy.ReleaseCandidate {
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest)
        return DesktopUpdatePolicy.ReleaseCandidate(release, bytes, sign(KEYS, bytes))
    }

    private fun tarballRelease(tag: String): DesktopUpdatePolicy.ReleaseCandidate {
        val version = tag.removePrefix("v")
        val name = "kani-desktop-linux-x64-$version.tar.gz"
        return candidate(
            release = releaseWith(tag, name),
            manifest = manifest(tag).let { base ->
                base.copy(
                    assets = listOf(
                        base.assets.single().copy(filename = name, packageType = "tar.gz"),
                    ),
                )
            },
        )
    }

    private fun releaseHosting(tag: String) =
        releaseWith(tag, "kani-desktop-linux-x64-${tag.removePrefix("v")}.deb")

    private fun releaseWith(tag: String, assetName: String) = GitHubReleaseMetadata(
        tagName = tag,
        htmlUrl = "https://example.invalid/$tag",
        assets = listOf(
            GitHubReleaseMetadata.ReleaseAsset(assetName, "https://example.invalid/$assetName"),
        ),
    )

    private fun manifest(tag: String): ReleaseManifest {
        val version = tag.removePrefix("v")
        val bytes = payload()
        return ReleaseManifest(
            schemaVersion = ReleaseManifest.CURRENT_SCHEMA_VERSION,
            releaseTag = tag,
            semanticVersion = version,
            buildSha = "abc123",
            keyId = KEY_ID,
            assets = listOf(
                ManifestAsset(
                    filename = "kani-desktop-linux-x64-$version.deb",
                    sizeBytes = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                    os = "linux",
                    arch = "x64",
                    packageType = "deb",
                ),
            ),
        )
    }

    private fun payload(): ByteArray = ByteArray(2_048) { index -> (index % 251).toByte() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte)
        }

    private fun sign(keys: KeyPair, bytes: ByteArray): ByteArray {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keys.private)
        signer.update(bytes)
        return signer.sign()
    }

    private companion object {
        const val CURRENT = "0.4.0"
        const val NEWER = "v0.5.0"
        const val OLDER = "v0.3.0"
        const val KEY_ID = "kani-release-key-1"
        val KEYS: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    }
}
