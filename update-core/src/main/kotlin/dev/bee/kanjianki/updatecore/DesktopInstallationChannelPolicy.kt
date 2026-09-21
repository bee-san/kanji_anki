package dev.bee.kanjianki.updatecore

import dev.bee.kanjianki.updatecore.DesktopUpdatePolicy.InstallationChannel

/**
 * Determines how the running desktop app was installed, from install-location evidence
 * (Goal 202).
 *
 * [DesktopUpdatePolicy] needs the channel before it will offer anything, because handing
 * a `.deb` to someone who unpacked a tarball installs a second divergent copy. This is
 * where that channel comes from.
 *
 * The evidence is the launcher path jpackage records in the `jpackage.app-path` system
 * property. A run that has no such path is not a packaged install at all — a Gradle run,
 * a source build, an IDE — so it resolves to [InstallationChannel.UNKNOWN] and is offered
 * nothing. That is the correct answer, not a gap: whoever built it owns updating it.
 *
 * Windows and macOS are unambiguous because Kani ships exactly one package for each, so
 * any packaged install there is that package. (Keying Windows off `Program Files` would
 * be wrong — an MSI can be installed per-user or to a chosen directory.) Linux is the
 * only host where two Kani packages exist, which is why the channel has to be worked out
 * from the path there:
 *
 * - `/opt/...` is jpackage's `.deb` install root, so it is the DEB channel;
 * - `/usr/...`, `/app/...` (Flatpak), and `/snap/...` are owned by a packager Kani does
 *   not ship, so they are UNKNOWN and Kani offers nothing;
 * - `/usr/local/...` and anywhere else a user can unpack an archive is the portable
 *   tarball, whose updates are manual.
 *
 * One acknowledged ambiguity: a tarball deliberately unpacked into `/opt` reads as a DEB
 * install. It would then be offered a `.deb`, and installing it would leave the unpacked
 * copy behind. Distinguishing them would need a marker file inside the image, which is
 * filesystem state this policy deliberately does not read; `/opt` is the documented deb
 * root and unpacking a tarball there is choosing to look like one.
 *
 * The resolved channel is [Resolution.tokenToPersist]ed to device-local settings so
 * Settings and diagnostics can report it. The stored value is never authoritative: the
 * path is re-read every launch, so upgrading from a tarball to a `.deb` is noticed rather
 * than remembered wrongly. The stored token exists to be *reported*, and to be corrected.
 */
object DesktopInstallationChannelPolicy {
    /**
     * The system property jpackage sets to the launcher's path in a packaged install.
     *
     * Named here rather than read here: this module stays pure so the Windows and macOS
     * mappings are testable on a Linux runner. The composition root passes the value in.
     */
    const val APP_PATH_PROPERTY: String = "jpackage.app-path"

    /**
     * `os.name` prefixes, lowercased.
     *
     * By prefix rather than substring, and macOS tested first, because `os.name` is
     * free-form vendor text and "darwin" contains "win" — a substring match would report
     * every macOS install as an MSI one. Matching Kani's other `os.name` reader,
     * `DesktopHostOs`, except that an unrecognized OS resolves to UNKNOWN here rather
     * than defaulting: an unfamiliar layout is still a directory Kani can write, but an
     * unfamiliar OS is not one Kani has a package for.
     */
    private const val WINDOWS = "win"
    private const val MAC = "mac"
    private const val DARWIN = "darwin"
    private const val LINUX = "linux"

    /** jpackage's default `.deb` install root. */
    const val LINUX_DEB_ROOT: String = "/opt/"

    /** Roots owned by a packager Kani does not ship, in longest-prefix-first order. */
    private val LINUX_UNMANAGED_ROOTS = listOf("/usr/", "/app/", "/snap/")

    /** A user-writable root that is a manual unpack, checked before `/usr/`. */
    private const val LINUX_PORTABLE_ROOT = "/usr/local/"

    /** The stable device-settings token for each channel. */
    fun storageToken(channel: InstallationChannel): String = when (channel) {
        InstallationChannel.WINDOWS_MSI -> "windows_msi"
        InstallationChannel.MACOS_DMG -> "macos_dmg"
        InstallationChannel.LINUX_DEB -> "linux_deb"
        InstallationChannel.LINUX_TAR_GZ -> "linux_tar_gz"
        InstallationChannel.UNKNOWN -> "unknown"
    }

    /**
     * The channel a stored token names, or [InstallationChannel.UNKNOWN] for an absent or
     * unrecognized one.
     *
     * Fails closed rather than guessing: an unreadable channel means Kani offers no
     * update, which is recoverable, while guessing means it may offer the wrong package.
     */
    fun fromStoredToken(token: String?): InstallationChannel {
        val normalized = token?.trim()?.lowercase().orEmpty()
        return InstallationChannel.entries.firstOrNull { storageToken(it) == normalized }
            ?: InstallationChannel.UNKNOWN
    }

    /**
     * The channel implied by [osName] and the jpackage launcher path [appPath].
     *
     * [appPath] is `null` for anything that is not a packaged install.
     */
    fun detect(osName: String, appPath: String?): InstallationChannel {
        val path = appPath?.trim().orEmpty()
        if (path.isEmpty()) return InstallationChannel.UNKNOWN
        val os = osName.lowercase()
        return when {
            // macOS before Windows: "darwin" contains "win".
            os.startsWith(MAC) || os.startsWith(DARWIN) -> InstallationChannel.MACOS_DMG
            os.startsWith(WINDOWS) -> InstallationChannel.WINDOWS_MSI
            os.startsWith(LINUX) -> detectLinux(path)
            else -> InstallationChannel.UNKNOWN
        }
    }

    private fun detectLinux(path: String): InstallationChannel = when {
        path.startsWith(LINUX_DEB_ROOT) -> InstallationChannel.LINUX_DEB
        // Before the /usr/ check below, which would otherwise swallow it.
        path.startsWith(LINUX_PORTABLE_ROOT) -> InstallationChannel.LINUX_TAR_GZ
        LINUX_UNMANAGED_ROOTS.any { path.startsWith(it) } -> InstallationChannel.UNKNOWN
        else -> InstallationChannel.LINUX_TAR_GZ
    }

    /** The channel to use now, and the token to write if the stored one is out of date. */
    data class Resolution(
        val channel: InstallationChannel,
        /** Non-null only when device settings must be updated. */
        val tokenToPersist: String?,
    )

    /**
     * Resolves the live channel from evidence and reports whether the stored token needs
     * rewriting.
     *
     * Detection wins over [storedToken] in every case, including when detection says
     * UNKNOWN: a packaged install that became an unpackaged one (or a package Kani no
     * longer recognizes) must stop being offered updates for the package it used to be.
     */
    fun resolve(osName: String, appPath: String?, storedToken: String?): Resolution {
        val detected = detect(osName, appPath)
        val token = storageToken(detected)
        return Resolution(
            channel = detected,
            tokenToPersist = token.takeIf { it != storedToken?.trim()?.lowercase() },
        )
    }
}
