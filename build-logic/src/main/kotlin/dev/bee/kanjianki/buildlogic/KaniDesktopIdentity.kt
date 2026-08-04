package dev.bee.kanjianki.buildlogic

object KaniDesktopIdentity {
    const val APPLICATION_NAME = "Kani"
    const val DESKTOP_ID = "dev.bee.kanjianki.desktop"
    const val MAIN_CLASS = "dev.bee.kanjianki.desktop.MainKt"
    const val WINDOWS_UPGRADE_UUID = "C972670E-BCCD-4D5E-9ACC-2C8877ABA799"
    const val DESCRIPTION = "Kanji study companion for Anki"
    const val VENDOR = "bee-san"
    const val ICON_DIRECTORY = "src/main/packaging/icons"
}

/**
 * The per-OS package version mappings for the desktop distributions (Goal 202).
 *
 * Each installer format has its own version grammar, and each mapping here is the
 * single place a release tag is converted for that format. They fail closed rather
 * than truncating: a version an installer cannot represent must break the release
 * build, because a silently truncated version produces an installer that refuses to
 * upgrade an existing install (or, worse, downgrades it) long after the release ships.
 */
object KaniDesktopPackageVersions {
    /** Windows Installer stores major and minor in one byte each. */
    const val MSI_MAJOR_MINOR_MAX: Int = 255

    /** Windows Installer stores the build (our patch) in two bytes. */
    const val MSI_PATCH_MAX: Int = 65_535

    /**
     * The Debian revision: the packaging of a given upstream version. Kani builds each
     * upstream version exactly once, so it is pinned rather than computed; a repackage
     * of an unchanged upstream version would bump this.
     */
    const val DEBIAN_REVISION: String = "1"

    /**
     * The macOS short version (`CFBundleShortVersionString`): the semantic version,
     * except that jpackage rejects a zero leading component, so a `0.x.y` line is
     * offset by one major. The offset is reversible and order-preserving, so the
     * short version stays monotonic across the eventual `0.x` → `1.0` boundary.
     */
    fun macOsJpackage(versionName: String): String {
        val version = KaniVersioning.parse(versionName)
        return "${version.major + 1}.${version.minor}.${version.patch}"
    }

    /**
     * The macOS bundle build version (`CFBundleVersion`): the monotonic Kani version
     * code, which is the same integer Android's `versionCode` uses. macOS compares
     * builds of the same short version by this value, so it must never decrease.
     */
    fun macOsBundleBuildVersion(versionName: String): String =
        KaniVersioning.parse(versionName).versionCode.toString()

    /**
     * The MSI `ProductVersion` — `major.minor.patch` within the installer's own numeric
     * bounds. Fails closed above them: MSI silently ignores the high bits, so a
     * truncated version would collide with an already-installed build and MSI would
     * decline the upgrade.
     */
    fun windowsMsi(versionName: String): String {
        val version = KaniVersioning.parse(versionName)
        require(version.major <= MSI_MAJOR_MINOR_MAX) {
            "MSI major component ${version.major} exceeds the installer maximum $MSI_MAJOR_MINOR_MAX"
        }
        require(version.minor <= MSI_MAJOR_MINOR_MAX) {
            "MSI minor component ${version.minor} exceeds the installer maximum $MSI_MAJOR_MINOR_MAX"
        }
        require(version.patch <= MSI_PATCH_MAX) {
            "MSI patch component ${version.patch} exceeds the installer maximum $MSI_PATCH_MAX"
        }
        return version.versionName
    }

    /** The Debian package version: the semantic version plus an explicit revision. */
    fun linuxDeb(versionName: String): String =
        "${KaniVersioning.parse(versionName).versionName}-$DEBIAN_REVISION"
}
