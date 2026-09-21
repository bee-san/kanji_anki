package dev.bee.kanjianki.updatecore

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUpdatePolicyTest {
    @Test
    fun aVerifiedNewerReleaseIsOfferedWithTheManifestsOwnSizeAndDigest() {
        val outcome = evaluate(current = "0.4.0", candidates = listOf(signedRelease("v0.5.0")))

        val available = outcome as DesktopUpdatePolicy.Outcome.UpdateAvailable
        assertEquals("v0.5.0", available.releaseTag)
        assertEquals("0.5.0", available.semanticVersion)
        assertEquals("https://example.invalid/kani-desktop-linux-x64-0.5.0.deb", available.downloadUrl)
        // The integrity facts come from the signed manifest, not the release listing.
        assertEquals(4_096L, available.asset.sizeBytes)
        assertEquals(DIGEST, available.asset.sha256)
        assertEquals(DesktopReleaseAssetSelector.DesktopPackageType.DEB, available.packageType)
        assertTrue("a .deb install updates in place", !available.manualInstall)
    }

    @Test
    fun anOlderOrEqualReleaseIsNeverOfferedAsADowngrade() {
        assertEquals(
            DesktopUpdatePolicy.Outcome.UpToDate,
            evaluate(current = "0.5.0", candidates = listOf(signedRelease("v0.5.0"))),
        )
        assertEquals(
            DesktopUpdatePolicy.Outcome.UpToDate,
            evaluate(current = "0.6.0", candidates = listOf(signedRelease("v0.5.0"))),
        )
    }

    @Test
    fun anAndroidOnlyNewestReleaseFallsBackToTheNewestCompleteDesktopRelease() {
        val androidOnly = DesktopUpdatePolicy.ReleaseCandidate(
            release = GitHubReleaseMetadata(
                tagName = "v0.6.0",
                htmlUrl = "https://example.invalid/v0.6.0",
                assets = listOf(
                    GitHubReleaseMetadata.ReleaseAsset(
                        "kani-android-0.6.0.apk",
                        "https://example.invalid/kani-android-0.6.0.apk",
                    ),
                ),
            ),
            manifestBytes = null,
            manifestSignature = null,
        )

        val outcome = evaluate(
            current = "0.4.0",
            candidates = listOf(androidOnly, signedRelease("v0.5.0")),
        )

        // A staged rollout: the desktop build for 0.6.0 is not published yet, so the
        // complete 0.5.0 release is offered rather than nothing.
        assertEquals("v0.5.0", (outcome as DesktopUpdatePolicy.Outcome.UpdateAvailable).releaseTag)
    }

    @Test
    fun aNewerReleaseWithoutAManifestIsUnavailableNotAnUnsignedInstall() {
        val unsigned = DesktopUpdatePolicy.ReleaseCandidate(
            release = releaseHosting("v0.5.0"),
            manifestBytes = null,
            manifestSignature = null,
        )

        val outcome = evaluate(current = "0.4.0", candidates = listOf(unsigned))

        // The asset is right there and hosted; without a signed manifest it is not offered.
        val unavailable = outcome as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("no signed desktop manifest"))
    }

    @Test
    fun aManifestSignedByAnUntrustedKeyIsRejected() {
        val attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val manifest = manifest("v0.5.0")
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest)
        val candidate = DesktopUpdatePolicy.ReleaseCandidate(
            release = releaseHosting("v0.5.0"),
            manifestBytes = bytes,
            // Correctly signed, but by a key the build does not ship.
            manifestSignature = sign(attacker, bytes),
        )

        val outcome = DesktopUpdatePolicy.evaluate(
            currentVersion = "0.4.0",
            channel = DesktopUpdatePolicy.InstallationChannel.LINUX_DEB,
            candidates = listOf(candidate),
            trustedKeys = mapOf(KEY_ID to KEYS.public.encoded),
        )

        val unavailable = outcome as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("rejected"))
    }

    @Test
    fun aReplayedManifestNamingAnotherReleaseIsRejected() {
        val replayed = candidate(release = releaseHosting("v0.5.0"), manifest = manifest("v0.4.9"))

        val unavailable = evaluate(current = "0.4.0", candidates = listOf(replayed))
            as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("names v0.4.9"))
    }

    @Test
    fun aManifestWhoseSemanticVersionDisagreesWithItsTagIsRejected() {
        val mismatched = candidate(
            release = releaseHosting("v0.5.0"),
            manifest = manifest("v0.5.0").copy(semanticVersion = "0.4.9"),
        )

        val unavailable = evaluate(current = "0.4.0", candidates = listOf(mismatched))
            as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("manifest version is 0.4.9"))
    }

    @Test
    fun aManifestWithoutThisPlatformsAssetIsUnavailable() {
        val windowsOnly = candidate(
            release = releaseHosting("v0.5.0"),
            manifest = manifest("v0.5.0").copy(
                assets = listOf(
                    asset("kani-desktop-windows-x64-0.5.0.msi", os = "windows", packageType = "msi"),
                ),
            ),
        )

        val unavailable = evaluate(current = "0.4.0", candidates = listOf(windowsOnly))
            as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("kani-desktop-linux-x64-0.5.0.deb yet"))
    }

    @Test
    fun aSignedManifestMislabellingAnAssetsPlatformIsRejected() {
        for (mislabelled in listOf(
            asset(DEB_NAME, os = "windows", packageType = "deb"),
            asset(DEB_NAME, arch = "arm64", packageType = "deb"),
            asset(DEB_NAME, packageType = "msi"),
        )) {
            val outcome = evaluate(
                current = "0.4.0",
                candidates = listOf(
                    candidate(
                        release = releaseHosting("v0.5.0"),
                        manifest = manifest("v0.5.0").copy(assets = listOf(mislabelled)),
                    ),
                ),
            )
            val unavailable = outcome as DesktopUpdatePolicy.Outcome.Unavailable
            assertTrue(unavailable.reason, unavailable.reason.contains("another platform"))
        }
    }

    @Test
    fun aManifestForAnAssetTheReleaseDoesNotHostIsUnavailable() {
        val notHosted = candidate(
            release = GitHubReleaseMetadata(
                tagName = "v0.5.0",
                htmlUrl = "https://example.invalid/v0.5.0",
                assets = emptyList(),
            ),
            manifest = manifest("v0.5.0"),
        )

        val unavailable = evaluate(current = "0.4.0", candidates = listOf(notHosted))
            as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("does not host"))
    }

    @Test
    fun aMalformedReleaseTagIsSkippedRatherThanTreatedAsVersionZero() {
        val garbage = candidate(release = releaseHosting("not-a-version"), manifest = manifest("v0.5.0"))

        // Skipped entirely: no newer release was seen, so this is "up to date", not a
        // downgrade to 0.0.0.
        assertEquals(
            DesktopUpdatePolicy.Outcome.UpToDate,
            evaluate(current = "0.4.0", candidates = listOf(garbage)),
        )
    }

    @Test
    fun theSearchIsBoundedToTheTenNewestReleases() {
        assertEquals(10, DesktopUpdatePolicy.MAX_RELEASES_SEARCHED)
        // Eleven newer releases, only the oldest of which is complete: out of range.
        val incomplete = List(10) {
            DesktopUpdatePolicy.ReleaseCandidate(
                release = releaseHosting("v0.9.$it"),
                manifestBytes = null,
                manifestSignature = null,
            )
        }

        val outcome = evaluate(
            current = "0.4.0",
            candidates = incomplete + signedRelease("v0.5.0"),
        )

        assertTrue(outcome is DesktopUpdatePolicy.Outcome.Unavailable)
    }

    @Test
    fun aPortableTarballInstallIsOfferedAsAManualDownload() {
        val channel = DesktopUpdatePolicy.InstallationChannel.LINUX_TAR_GZ
        assertTrue("a tarball never drives automatic handoff", !channel.participatesInAutomaticHandoff)
        val tarball = candidate(
            release = GitHubReleaseMetadata(
                tagName = "v0.5.0",
                htmlUrl = "https://example.invalid/v0.5.0",
                assets = listOf(
                    GitHubReleaseMetadata.ReleaseAsset(
                        TAR_NAME,
                        "https://example.invalid/$TAR_NAME",
                    ),
                ),
            ),
            manifest = manifest("v0.5.0").copy(
                assets = listOf(asset(TAR_NAME, packageType = "tar.gz")),
            ),
        )

        val outcome = DesktopUpdatePolicy.evaluate(
            currentVersion = "0.4.0",
            channel = channel,
            candidates = listOf(tarball),
            trustedKeys = mapOf(KEY_ID to KEYS.public.encoded),
        )

        val available = outcome as DesktopUpdatePolicy.Outcome.UpdateAvailable
        assertTrue("the user installs a tarball themselves", available.manualInstall)
    }

    @Test
    fun anUnknownInstallationChannelIsNeverOfferedAnUpdate() {
        val channel = DesktopUpdatePolicy.InstallationChannel.UNKNOWN
        assertTrue(!channel.participatesInAutomaticHandoff)

        val outcome = DesktopUpdatePolicy.evaluate(
            currentVersion = "0.4.0",
            channel = channel,
            candidates = listOf(signedRelease("v0.5.0")),
            trustedKeys = mapOf(KEY_ID to KEYS.public.encoded),
        )

        val unavailable = outcome as DesktopUpdatePolicy.Outcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("not one Kani updates"))
    }

    @Test
    fun everyChannelWithAPackageTypeMapsToItsOwnTargetAndCanonicalName() {
        for (channel in DesktopUpdatePolicy.InstallationChannel.entries) {
            val target = channel.target
            val type = channel.packageType
            if (target == null || type == null) {
                assertEquals(DesktopUpdatePolicy.InstallationChannel.UNKNOWN, channel)
                continue
            }
            val name = DesktopReleaseAssetSelector.canonicalName(target, type, "1.2.3")
            assertTrue(name, name.endsWith(type.extension))
            assertEquals(type.extension.removePrefix("."), type.manifestToken)
        }
    }

    @Test
    fun anEmptyCandidateListIsUpToDate() {
        assertEquals(
            DesktopUpdatePolicy.Outcome.UpToDate,
            evaluate(current = "0.4.0", candidates = emptyList()),
        )
    }

    private fun evaluate(
        current: String,
        candidates: List<DesktopUpdatePolicy.ReleaseCandidate>,
    ): DesktopUpdatePolicy.Outcome = DesktopUpdatePolicy.evaluate(
        currentVersion = current,
        channel = DesktopUpdatePolicy.InstallationChannel.LINUX_DEB,
        candidates = candidates,
        trustedKeys = mapOf(KEY_ID to KEYS.public.encoded),
    )

    private fun signedRelease(tag: String) =
        candidate(release = releaseHosting(tag), manifest = manifest(tag))

    private fun candidate(release: GitHubReleaseMetadata, manifest: ReleaseManifest): DesktopUpdatePolicy.ReleaseCandidate {
        val bytes = ReleaseManifestCodec.canonicalBytes(manifest)
        return DesktopUpdatePolicy.ReleaseCandidate(release, bytes, sign(KEYS, bytes))
    }

    private fun releaseHosting(tag: String): GitHubReleaseMetadata {
        val name = "kani-desktop-linux-x64-${tag.removePrefix("v")}.deb"
        return GitHubReleaseMetadata(
            tagName = tag,
            htmlUrl = "https://example.invalid/$tag",
            assets = listOf(GitHubReleaseMetadata.ReleaseAsset(name, "https://example.invalid/$name")),
        )
    }

    private fun manifest(tag: String): ReleaseManifest {
        val version = tag.removePrefix("v")
        return ReleaseManifest(
            schemaVersion = ReleaseManifest.CURRENT_SCHEMA_VERSION,
            releaseTag = tag,
            semanticVersion = version,
            buildSha = "abc123",
            keyId = KEY_ID,
            assets = listOf(asset("kani-desktop-linux-x64-$version.deb")),
        )
    }

    private fun asset(
        filename: String,
        os: String = "linux",
        arch: String = "x64",
        packageType: String = "deb",
    ) = ManifestAsset(
        filename = filename,
        sizeBytes = 4_096L,
        sha256 = DIGEST,
        os = os,
        arch = arch,
        packageType = packageType,
    )

    private fun sign(keys: KeyPair, bytes: ByteArray): ByteArray {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keys.private)
        signer.update(bytes)
        return signer.sign()
    }

    private companion object {
        const val KEY_ID = "kani-release-key-1"
        val DIGEST = "c".repeat(64)
        const val DEB_NAME = "kani-desktop-linux-x64-0.5.0.deb"
        const val TAR_NAME = "kani-desktop-linux-x64-0.5.0.tar.gz"
        val KEYS: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    }
}
