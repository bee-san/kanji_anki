package dev.bee.kanjianki.updatecore

/**
 * Selects the desktop release asset for a host's OS and architecture, by exact
 * canonical name.
 *
 * Goal 202 extends update selection from APK-only to native desktop packages, and
 * pins the canonical asset names so a release's assets are matched, never guessed:
 *
 * - `kani-desktop-windows-x64-<version>.msi`
 * - `kani-desktop-linux-x64-<version>.deb`
 * - `kani-desktop-linux-x64-<version>.tar.gz`
 * - `kani-desktop-macos-arm64-<version>.dmg`
 *
 * Android's [UpdateReleaseAssetSelector] is deliberately untouched — this is a
 * separate selector, so the Android updater keeps selecting only its verified APK.
 *
 * Matching is by exact filename against the [DesktopTarget]'s allowed package types in
 * preference order, so a Linux host prefers the `.deb` (which participates in automatic
 * handoff) over the portable `.tar.gz` (manual). A missing or wrong-arch asset is a
 * failure, not a fallback to some other platform's package: installing the wrong
 * architecture is worse than reporting "no desktop build for this host yet", which is a
 * normal staged-rollout state.
 */
object DesktopReleaseAssetSelector {
    private const val PREFIX = "kani-desktop"

    /** The host targets Kani ships desktop packages for. */
    enum class DesktopTarget(
        val osToken: String,
        val archToken: String,
        /** Accepted package types, most-preferred first. */
        val packageTypes: List<DesktopPackageType>,
    ) {
        WINDOWS_X64("windows", "x64", listOf(DesktopPackageType.MSI)),
        MACOS_ARM64("macos", "arm64", listOf(DesktopPackageType.DMG)),
        LINUX_X64("linux", "x64", listOf(DesktopPackageType.DEB, DesktopPackageType.TAR_GZ)),
    }

    enum class DesktopPackageType(val extension: String, val participatesInAutomaticHandoff: Boolean) {
        MSI(".msi", true),
        DMG(".dmg", true),
        DEB(".deb", true),
        // Portable tarball: updates are manual, so it never drives automatic handoff.
        TAR_GZ(".tar.gz", false),
    }

    data class Selection(
        val asset: GitHubReleaseMetadata.ReleaseAsset,
        val packageType: DesktopPackageType,
    )

    /**
     * The canonical asset name for [target]'s [type] at [version].
     *
     * The single source of truth both the selector and the release build use, so a
     * name typo cannot make them disagree.
     */
    fun canonicalName(target: DesktopTarget, type: DesktopPackageType, version: String): String =
        "$PREFIX-${target.osToken}-${target.archToken}-$version${type.extension}"

    /**
     * The best matching asset in [release] for [target] at [version], or null when the
     * release carries no package for this host — a normal staged-rollout state the
     * caller treats as "no update", never a wrong-platform fallback.
     */
    fun select(
        release: GitHubReleaseMetadata?,
        target: DesktopTarget,
        version: String,
    ): Selection? {
        val assets = release?.assets() ?: return null
        for (type in target.packageTypes) {
            val name = canonicalName(target, type, version)
            val asset = assets.firstOrNull { it.name() == name }
            if (asset != null) return Selection(asset, type)
        }
        return null
    }
}
