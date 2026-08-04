package dev.bee.kanjianki.buildlogic

object KaniDesktopIdentity {
    const val APPLICATION_NAME = "Kani"
    const val DESKTOP_ID = "dev.bee.kanjianki.desktop"
    const val MAIN_CLASS = "dev.bee.kanjianki.desktop.MainKt"
    const val WINDOWS_UPGRADE_UUID = "C972670E-BCCD-4D5E-9ACC-2C8877ABA799"
    const val DESCRIPTION = "Kanji study companion for Anki"
    const val VENDOR = "bee-san"
    const val PACKAGING_DIRECTORY = "src/main/packaging"
    const val ICON_DIRECTORY = "$PACKAGING_DIRECTORY/icons"

    /**
     * The Start-menu / application-menu group the installers place Kani under.
     *
     * One shallow group named for the application rather than a category: a category
     * group ("Education") would collide with other vendors' groups and make an
     * uninstall's leftovers ambiguous.
     */
    const val MENU_GROUP = "Kani"

    /**
     * The copyright line shown by the Windows installer, macOS bundle, and DEB control
     * file. Not a license grant — see `docs/desktop-native-packaging.md` for the
     * still-open licensing item.
     */
    const val COPYRIGHT = "Copyright (c) bee-san"

    /**
     * The Debian binary package name.
     *
     * Lowercase, because `dpkg` rejects an uppercase package name. jpackage otherwise
     * derives it from `packageName` (`Kani`) and fails during packaging, on a host that
     * has the DEB tooling — which is not the host most of this work happens on.
     */
    const val LINUX_PACKAGE_NAME = "kani"

    /**
     * The freedesktop menu category. `Education` is the registered main category that
     * matches a study application; the additional `Languages` category would need a
     * hand-written desktop entry, which jpackage does not expose.
     */
    const val LINUX_APP_CATEGORY = "Education"

    /**
     * The Debian `Maintainer:` field, which is mandatory in a control file. jpackage
     * substitutes a placeholder when it is unset and lintian flags that.
     */
    const val LINUX_DEB_MAINTAINER = "bee-san <bee@bee.gg>"

    /**
     * The oldest macOS release Kani's package is qualified for.
     *
     * Pinned at 13 (Ventura) per Goal 204, which also requires that the floor be raised
     * to the oldest version *actually tested* if a real qualification gate is not
     * available. It is not yet: this value is a declared deployment target, and
     * `docs/desktop-native-packaging.md` records that the macOS 13 hardware run has not
     * happened. `LSMinimumSystemVersion` makes the claim enforceable at launch — an
     * older system refuses to open the app instead of failing somewhere inside Skiko.
     */
    const val MACOS_MINIMUM_SYSTEM_VERSION = "13.0"

    /**
     * The macOS App Store category. Kani is not distributed through the App Store, but
     * `LSApplicationCategoryType` is also read by Finder and by Launchpad grouping.
     */
    const val MACOS_APP_CATEGORY = "public.app-category.education"
}

/**
 * The JDK modules Kani's packaged runtime image must contain beyond jpackage's default.
 *
 * `jpackage` builds a minimal `jlink` image, and a module that is missing from it does
 * not fail the build — it fails at the first line of code that touches it, in the
 * installed application, on the user's machine. Kani found this the way it is usually
 * found: the packaged image launched, rendered, and then threw
 * `NoClassDefFoundError: java/net/http/HttpConnectTimeoutException` out of the
 * composition root the moment it probed AnkiConnect, while every Gradle-run and
 * `:desktop-app:run` launch — which use the full JDK — had always worked.
 *
 * Each entry is here because something on a real launch path needs it:
 *
 *  - `java.net.http` is the AnkiConnect transport. Every provider call on desktop is
 *    an HTTP request to loopback, so without this module the desktop host has no
 *    provider at all.
 *  - `java.instrument` and `jdk.unsupported` are required by the Kotlin/Compose
 *    runtime stack rather than by Kani's own code, which is exactly why they cannot be
 *    reasoned about from Kani's sources and must be pinned from the dependency scan.
 *  - `jdk.accessibility` is the Windows Java Access Bridge, which is how NVDA and JAWS
 *    read a Compose window at all. Compose Multiplatform's own accessibility page
 *    requires it in `nativeDistributions` for exactly this reason. It is the one entry
 *    the dependency scan cannot find, because nothing on Kani's classpath references it
 *    — the JDK loads it reflectively when the bridge is enabled — so an image built from
 *    the scan alone ships a Windows build that is silent to a screen reader while every
 *    semantics assertion in the suite still passes.
 *
 * Regenerate with `./gradlew :desktop-app:suggestRuntimeModules`, which reports the
 * modules the packaged classpath actually references. Add what it names rather than
 * guessing, and never remove an entry to make an image smaller: the cost of an extra
 * module is disk, and the cost of a missing one is a crash — or a mute screen reader —
 * that no unit test can see.
 */
object KaniDesktopRuntimeModules {
    val REQUIRED: List<String> = listOf(
        "java.instrument",
        "java.net.http",
        "jdk.accessibility",
        "jdk.unsupported",
    )
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
