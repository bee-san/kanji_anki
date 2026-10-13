package dev.bee.kanjianki.updatecore

/**
 * Decides whether a desktop update may be offered, from the newest releases and their
 * signed manifests (Goal 202).
 *
 * The gate every desktop update passes before a byte is downloaded. It checks, in order:
 * the running installation channel (an app that cannot tell how it was installed must
 * not hand an installer to the OS), the release tag being strictly newer, the manifest's
 * Ed25519 signature under a shipped key, the manifest agreeing with the release tag,
 * and the manifest describing an asset for this exact OS, architecture, and package type
 * that the release actually hosts.
 *
 * Two deliberate non-failures: a newest release carrying only the Android APK, and a
 * release whose desktop manifest is not published yet, are both [Outcome.Unavailable] —
 * a normal staged rollout, not an error. So the search looks at up to
 * [MAX_RELEASES_SEARCHED] of the newest releases for the newest one that is fully valid,
 * rather than giving up at the first release without a desktop build. It is bounded
 * because an unbounded walk back through release history would eventually reach releases
 * predating desktop support and spend a request on each.
 *
 * There is no unsigned path. A release hosting a matching asset but no valid manifest is
 * skipped, never installed from its co-hosted checksum: whoever can replace the asset can
 * replace that checksum too.
 */
object DesktopUpdatePolicy {
    /** The bound on how far back a search walks for a valid signed desktop manifest. */
    const val MAX_RELEASES_SEARCHED: Int = 10

    /**
     * How the running app was installed, recorded at install time.
     *
     * It selects the update's package type rather than letting the updater prefer one:
     * a portable-tarball install must be offered the tarball, and handing a `.deb` to a
     * user who unpacked a tarball would install a second, divergent copy. Only channels
     * whose package type participates in automatic handoff can be updated in place;
     * [LINUX_TAR_GZ] is offered as a manual download.
     */
    enum class InstallationChannel(
        val target: DesktopReleaseAssetSelector.DesktopTarget?,
        val packageType: DesktopReleaseAssetSelector.DesktopPackageType?,
    ) {
        WINDOWS_MSI(
            DesktopReleaseAssetSelector.DesktopTarget.WINDOWS_X64,
            DesktopReleaseAssetSelector.DesktopPackageType.MSI,
        ),
        MACOS_DMG(
            DesktopReleaseAssetSelector.DesktopTarget.MACOS_ARM64,
            DesktopReleaseAssetSelector.DesktopPackageType.DMG,
        ),
        LINUX_DEB(
            DesktopReleaseAssetSelector.DesktopTarget.LINUX_X64,
            DesktopReleaseAssetSelector.DesktopPackageType.DEB,
        ),
        LINUX_TAR_GZ(
            DesktopReleaseAssetSelector.DesktopTarget.LINUX_X64,
            DesktopReleaseAssetSelector.DesktopPackageType.TAR_GZ,
        ),

        /**
         * The install was not recorded, or was made by a packager Kani does not ship
         * (a distro repository, a Flatpak, a source build). Such an install is updated
         * by whoever owns it, so Kani offers nothing.
         */
        UNKNOWN(null, null),
        ;

        /** Whether Kani may download and open an installer for this channel. */
        val participatesInAutomaticHandoff: Boolean
            get() = packageType?.participatesInAutomaticHandoff == true
    }

    /** A release plus the manifest bytes and detached signature fetched alongside it. */
    class ReleaseCandidate(
        val release: GitHubReleaseMetadata,
        val manifestBytes: ByteArray?,
        val manifestSignature: ByteArray?,
    )

    sealed interface Outcome {
        /**
         * A verified newer release for this channel. [asset] carries the size and
         * SHA-256 the download must match, from the signed manifest — never from the
         * release listing, which is not signed.
         */
        data class UpdateAvailable(
            val releaseTag: String,
            val semanticVersion: String,
            val downloadUrl: String,
            val asset: ManifestAsset,
            val packageType: DesktopReleaseAssetSelector.DesktopPackageType,
            /** True when the user must install it themselves (portable tarball). */
            val manualInstall: Boolean,
        ) : Outcome

        /** No release is newer than what is running. */
        data object UpToDate : Outcome

        /**
         * Something newer exists but is not offerable — no desktop asset yet, no valid
         * manifest yet, or an unknown installation channel. A normal state, not a
         * failure the user needs to act on.
         */
        data class Unavailable(val reason: String) : Outcome
    }

    /**
     * The newest offerable update for [channel], searching at most
     * [MAX_RELEASES_SEARCHED] of [candidates] (newest first).
     *
     * [trustedKeys] maps key id to X.509-encoded Ed25519 public key; a rotation release
     * trusts both the old and the new key. Returns [Outcome.UpToDate] only when no
     * candidate is newer than [currentVersion] at all, so a newer-but-unverifiable
     * release is never reported as "you are up to date".
     */
    fun evaluate(
        currentVersion: String,
        channel: InstallationChannel,
        candidates: List<ReleaseCandidate>,
        trustedKeys: Map<String, ByteArray>,
    ): Outcome {
        val target = channel.target
        val packageType = channel.packageType
        if (target == null || packageType == null) {
            return Outcome.Unavailable("installation channel is not one Kani updates")
        }

        var sawNewerRelease = false
        var firstRejection: String? = null
        for (candidate in candidates.take(MAX_RELEASES_SEARCHED)) {
            val tag = candidate.release.tagName()
            // Never downgrade, and never treat a malformed tag as version 0.0.0.
            if (!ReleaseVersion.isValidSemver(tag)) continue
            if (!ReleaseVersion.isNewerSemver(currentVersion, tag)) continue
            sawNewerRelease = true

            when (val offer = offerFrom(candidate, tag, target, packageType, trustedKeys)) {
                is Outcome.UpdateAvailable -> return offer
                // Keep walking: this release may be Android-only or not yet signed
                // while an older-but-still-newer release is complete.
                else -> if (firstRejection == null) {
                    firstRejection = (offer as Outcome.Unavailable).reason
                }
            }
        }

        if (!sawNewerRelease) return Outcome.UpToDate
        return Outcome.Unavailable(firstRejection ?: "no newer desktop release is available yet")
    }

    private fun offerFrom(
        candidate: ReleaseCandidate,
        tag: String,
        target: DesktopReleaseAssetSelector.DesktopTarget,
        packageType: DesktopReleaseAssetSelector.DesktopPackageType,
        trustedKeys: Map<String, ByteArray>,
    ): Outcome {
        val manifestBytes = candidate.manifestBytes
        val signature = candidate.manifestSignature
        if (manifestBytes == null || signature == null) {
            return Outcome.Unavailable("release $tag has no signed desktop manifest yet")
        }

        val manifest = when (val verdict = ReleaseManifestVerifier.verify(manifestBytes, signature, trustedKeys)) {
            is ReleaseManifestVerifier.Result.Verified -> verdict.manifest
            is ReleaseManifestVerifier.Result.Rejected ->
                return Outcome.Unavailable("release $tag manifest rejected: ${verdict.reason}")
        }

        // The manifest must describe the release it was fetched from. A manifest that
        // verifies but names a different release is a replay of an older signed
        // manifest onto a newer tag.
        if (manifest.releaseTag != tag) {
            return Outcome.Unavailable("release $tag manifest names ${manifest.releaseTag}")
        }
        if (manifest.semanticVersion != tag.removePrefix("v")) {
            return Outcome.Unavailable("release $tag manifest version is ${manifest.semanticVersion}")
        }

        val expectedName = DesktopReleaseAssetSelector.canonicalName(
            target = target,
            type = packageType,
            version = manifest.semanticVersion,
        )
        val manifestAsset = manifest.assets.firstOrNull { it.filename == expectedName }
            ?: return Outcome.Unavailable("release $tag has no $expectedName yet")
        // The manifest is authoritative for platform identity too, so a signed manifest
        // mislabelling an asset's OS/arch/package cannot slip a wrong build through.
        if (manifestAsset.os != target.osToken ||
            manifestAsset.arch != target.archToken ||
            manifestAsset.packageType != packageType.manifestToken
        ) {
            return Outcome.Unavailable("release $tag manifest describes $expectedName for another platform")
        }

        val hosted = candidate.release.assets().firstOrNull { it.name() == expectedName }
            ?: return Outcome.Unavailable("release $tag does not host $expectedName")

        return Outcome.UpdateAvailable(
            releaseTag = tag,
            semanticVersion = manifest.semanticVersion,
            downloadUrl = hosted.downloadUrl(),
            asset = manifestAsset,
            packageType = packageType,
            manualInstall = !packageType.participatesInAutomaticHandoff,
        )
    }
}
