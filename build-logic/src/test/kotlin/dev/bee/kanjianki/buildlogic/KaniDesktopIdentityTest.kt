package dev.bee.kanjianki.buildlogic

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniDesktopIdentityTest {
    @Test
    fun packageIdentityIsStableAndExact() {
        assertEquals("Kani", KaniDesktopIdentity.APPLICATION_NAME)
        assertEquals(
            "dev.bee.kanjianki.desktop",
            KaniDesktopIdentity.DESKTOP_ID,
        )
        assertEquals(
            "dev.bee.kanjianki.desktop.MainKt",
            KaniDesktopIdentity.MAIN_CLASS,
        )
        assertEquals(
            "C972670E-BCCD-4D5E-9ACC-2C8877ABA799",
            KaniDesktopIdentity.WINDOWS_UPGRADE_UUID,
        )
        assertEquals(
            UUID.fromString(KaniDesktopIdentity.WINDOWS_UPGRADE_UUID),
            UUID.fromString("C972670E-BCCD-4D5E-9ACC-2C8877ABA799"),
        )
        assertTrue(
            KaniDesktopIdentity.DESKTOP_ID.matches(
                Regex("[A-Za-z0-9.-]+"),
            ),
        )
    }

    /**
     * The runtime image must carry the modules the installed app needs.
     *
     * Pinned as an exact list rather than a containment check, because the failure this
     * guards is a module *disappearing*: the packaged app then launches, renders, and
     * throws `NoClassDefFoundError` on the first provider call, which is a defect only
     * a real installed-image launch can see. Two entries are called out by name because
     * losing either is silent in a different way: `java.net.http` is the entire desktop
     * provider transport, and `jdk.accessibility` is the Windows Java Access Bridge, so
     * an image without it renders perfectly and reads as an empty window to NVDA and
     * JAWS while every semantics assertion in the suite still passes.
     */
    @Test
    fun theRuntimeImageCarriesEveryModuleTheInstalledAppNeeds() {
        assertEquals(
            listOf(
                "java.instrument",
                "java.net.http",
                "jdk.accessibility",
                "jdk.unsupported",
            ),
            KaniDesktopRuntimeModules.REQUIRED,
        )
        assertTrue(
            "The AnkiConnect transport needs java.net.http in the packaged runtime",
            "java.net.http" in KaniDesktopRuntimeModules.REQUIRED,
        )
        assertTrue(
            "The Windows Java Access Bridge needs jdk.accessibility in the image",
            "jdk.accessibility" in KaniDesktopRuntimeModules.REQUIRED,
        )

        val repositoryRoot = File(
            requireNotNull(System.getProperty("kani.repositoryRoot")),
        )
        val convention = File(
            repositoryRoot,
            "build-logic/src/main/kotlin/" +
                "kani.desktop-application-conventions.gradle.kts",
        ).readText()
        // Read from the pinned list rather than spelled again in the convention, so
        // this test and the packaged image cannot disagree about which modules ship.
        assertTrue(
            "The desktop package must configure modules from the pinned list",
            convention.contains("modules(*KaniDesktopRuntimeModules.REQUIRED.toTypedArray())"),
        )
    }

    @Test
    fun macOsJpackageVersionIsValidReversibleAndMonotonic() {
        assertEquals(
            "1.4.33",
            KaniDesktopPackageVersions.macOsJpackage("0.4.33"),
        )
        assertEquals(
            "2.0.0",
            KaniDesktopPackageVersions.macOsJpackage("1.0.0"),
        )
        assertEquals(
            "13.34.56",
            KaniDesktopPackageVersions.macOsJpackage("12.34.56"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            KaniDesktopPackageVersions.macOsJpackage("not-semantic")
        }
    }

    @Test
    fun macOsBundleBuildVersionIsTheMonotonicVersionCode() {
        assertEquals(
            "4033",
            KaniDesktopPackageVersions.macOsBundleBuildVersion("0.4.33"),
        )
        assertEquals(
            "1000000",
            KaniDesktopPackageVersions.macOsBundleBuildVersion("1.0.0"),
        )
        // The build version orders two releases sharing a short version, so it must
        // increase whenever the semantic version does.
        val earlier = KaniDesktopPackageVersions.macOsBundleBuildVersion("0.4.33").toInt()
        val later = KaniDesktopPackageVersions.macOsBundleBuildVersion("0.5.0").toInt()
        assertTrue("bundle build version must be monotonic", earlier < later)
        assertThrows(IllegalArgumentException::class.java) {
            KaniDesktopPackageVersions.macOsBundleBuildVersion("not-semantic")
        }
    }

    @Test
    fun msiVersionUsesMajorMinorPatchAndFailsClosedAboveInstallerBounds() {
        assertEquals("0.4.33", KaniDesktopPackageVersions.windowsMsi("0.4.33"))
        assertEquals(
            "255.255.999",
            KaniDesktopPackageVersions.windowsMsi("255.255.999"),
        )

        val major = assertThrows(IllegalArgumentException::class.java) {
            KaniDesktopPackageVersions.windowsMsi("256.0.0")
        }
        assertTrue(
            "the MSI major guard must name the bound",
            major.message.orEmpty().contains("255"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            KaniDesktopPackageVersions.windowsMsi("0.256.0")
        }
        // The canonical release grammar caps every component at 999, well under MSI's
        // 65535 patch bound, so the patch guard is a defence in depth rather than a
        // reachable release state; assert the bound itself instead of a rejected tag.
        assertEquals(65_535, KaniDesktopPackageVersions.MSI_PATCH_MAX)
        assertTrue(
            "the release grammar must stay inside the MSI patch bound",
            KaniDesktopPackageVersions.windowsMsi("0.0.999").endsWith("999"),
        )
    }

    @Test
    fun debVersionIsSemanticVersionPlusAnExplicitRevision() {
        assertEquals("1", KaniDesktopPackageVersions.DEBIAN_REVISION)
        assertEquals("0.4.33-1", KaniDesktopPackageVersions.linuxDeb("v0.4.33"))
        assertEquals("1.0.0-1", KaniDesktopPackageVersions.linuxDeb("1.0.0"))
        assertThrows(IllegalArgumentException::class.java) {
            KaniDesktopPackageVersions.linuxDeb("1.0")
        }
    }

    @Test
    fun desktopConventionConsumesThePinnedIdentityAndAllIconFormats() {
        val repositoryRoot = File(
            requireNotNull(System.getProperty("kani.repositoryRoot")),
        )
        val convention = File(
            repositoryRoot,
            "build-logic/src/main/kotlin/" +
                "kani.desktop-application-conventions.gradle.kts",
        ).readText()

        for (identityProperty in listOf(
            "MAIN_CLASS",
            "APPLICATION_NAME",
            "DESKTOP_ID",
            "WINDOWS_UPGRADE_UUID",
            "DESCRIPTION",
            "VENDOR",
        )) {
            assertTrue(
                "$identityProperty must configure the desktop package",
                convention.contains("KaniDesktopIdentity.$identityProperty"),
            )
        }
        for (extension in listOf("icns", "ico", "png")) {
            assertTrue(
                "The .$extension package icon must be configured",
                convention.contains("/kani.$extension"),
            )
        }
        assertTrue(
            "macOS package version must use the jpackage-compatible mapping",
            convention.contains(
                "KaniDesktopPackageVersions.macOsJpackage(kaniVersionName)",
            ),
        )
        // Every platform version must come from the pinned mapping, so no installer
        // silently inherits the raw semantic version its grammar cannot represent.
        for (mapping in listOf(
            "macOsBundleBuildVersion(kaniVersionName)",
            "windowsMsi(kaniVersionName)",
            "linuxDeb(kaniVersionName)",
        )) {
            assertTrue(
                "$mapping must configure the desktop package version",
                convention.contains("KaniDesktopPackageVersions.$mapping"),
            )
        }
        assertEquals(
            setOf("Dmg", "Msi", "Deb"),
            Regex("""TargetFormat\.([A-Za-z]+)""")
                .findAll(convention)
                .map { match -> match.groupValues[1] }
                .toSet(),
        )
    }

    /**
     * The Windows package installs per-user and needs no administrator.
     *
     * This is the substantive choice in the MSI, and it follows from where Kani keeps
     * user data: `%LOCALAPPDATA%\Kani` and `%APPDATA%\Kani`. A per-machine install would
     * demand elevation for every update while still writing per-user data, so it would
     * gain nothing and cost a UAC prompt per release — and an uninstall removing
     * per-machine program files could not be reasoned about against a per-user profile
     * as cleanly. Pinned here because `perUserInstall` defaults to `false`: losing the
     * line is a silent switch back to an elevated install, visible only to a user on a
     * machine where they are not an administrator.
     */
    @Test
    fun theWindowsPackageInstallsPerUserWithoutElevation() {
        val convention = conventionSource()

        assertTrue(
            "the MSI must install per-user so updates need no administrator",
            convention.contains("perUserInstall = true"),
        )
        assertTrue(
            "the MSI must create a Start-menu entry",
            convention.contains("menu = true"),
        )
        assertTrue(
            "the MSI must place its entry in the pinned menu group",
            convention.contains("menuGroup = KaniDesktopIdentity.MENU_GROUP"),
        )
        assertTrue(
            "the MSI must offer a desktop shortcut",
            convention.contains("shortcut = true"),
        )
        assertTrue(
            "a per-user install must let the user choose the directory",
            convention.contains("dirChooser = true"),
        )
        // A GUI application must not attach a console window to a double-click launch.
        assertTrue(
            "the Windows package must not be a console application",
            convention.contains("console = false"),
        )
    }

    /**
     * The DEB carries a launcher entry, a category, and a maintainer.
     *
     * `packageName` is the load-bearing one: `dpkg` rejects an uppercase package name, so
     * without the lowercase override jpackage derives `Kani` from the application name
     * and the build fails during packaging — on a host that has the DEB tooling, which is
     * not the host this work is developed on. That makes it exactly the kind of defect
     * that first appears in CI or in a release.
     */
    @Test
    fun theDebianPackageIsInstallableAndAppearsInTheApplicationMenu() {
        val convention = conventionSource()

        assertEquals("kani", KaniDesktopIdentity.LINUX_PACKAGE_NAME)
        assertTrue(
            "the Debian package name must be dpkg-legal lowercase",
            KaniDesktopIdentity.LINUX_PACKAGE_NAME
                .matches(Regex("[a-z0-9][a-z0-9+.-]+")),
        )
        assertTrue(
            "the DEB must override the package name for dpkg",
            convention.contains(
                "packageName = KaniDesktopIdentity.LINUX_PACKAGE_NAME",
            ),
        )
        assertTrue(
            "the DEB must install a desktop entry",
            convention.contains("shortcut = true"),
        )
        assertTrue(
            "the DEB must declare a freedesktop category",
            convention.contains(
                "appCategory = KaniDesktopIdentity.LINUX_APP_CATEGORY",
            ),
        )
        assertEquals("Education", KaniDesktopIdentity.LINUX_APP_CATEGORY)
        assertTrue(
            "a Debian control file requires a maintainer",
            convention.contains(
                "debMaintainer = KaniDesktopIdentity.LINUX_DEB_MAINTAINER",
            ),
        )
        assertTrue(
            "the maintainer field must carry a contact address",
            KaniDesktopIdentity.LINUX_DEB_MAINTAINER.matches(
                Regex(""".+ <[^@\s]+@[^@\s]+>"""),
            ),
        )
    }

    /**
     * The macOS bundle declares its minimum system and is ready to be signed.
     *
     * `minimumSystemVersion` becomes `LSMinimumSystemVersion`, which makes the support
     * claim enforceable at launch: an older system refuses to open the bundle instead of
     * opening it and failing inside Skiko, where the cause is unrecoverable from a user's
     * report. Kani is not signed yet, and the entitlements files exist so that enabling
     * signing later is a credential change rather than a behavioral one — a missing JVM
     * entitlement is invisible until the first signed build, i.e. until a release.
     */
    @Test
    fun theMacOsBundleDeclaresItsMinimumSystemAndIsSigningReady() {
        val convention = conventionSource()

        assertEquals("13.0", KaniDesktopIdentity.MACOS_MINIMUM_SYSTEM_VERSION)
        assertTrue(
            "the bundle must declare its minimum macOS version",
            convention.contains(
                "minimumSystemVersion = KaniDesktopIdentity.MACOS_MINIMUM_SYSTEM_VERSION",
            ),
        )
        assertTrue(
            "the bundle must declare an application category",
            convention.contains(
                "appCategory = KaniDesktopIdentity.MACOS_APP_CATEGORY",
            ),
        )
        assertTrue(
            "the app bundle must have hardened-runtime entitlements",
            convention.contains("entitlementsFile.set("),
        )
        assertTrue(
            "the embedded JDK runtime is signed separately and needs its own file",
            convention.contains("runtimeEntitlementsFile.set("),
        )
    }

    /**
     * The entitlements the JVM needs under the hardened runtime are present, and the
     * sandbox entitlements that would misrepresent Kani are not.
     *
     * Read as files rather than asserted from the convention, because their *content* is
     * the contract. A signed, notarized Kani missing `allow-jit` crashes at launch on a
     * user's machine while every unsigned local build works, and nothing before the first
     * signed release would say so.
     */
    @Test
    fun theHardenedRuntimeEntitlementsGrantWhatTheJvmNeedsAndNothingMisleading() {
        val required = listOf(
            "com.apple.security.cs.allow-jit",
            "com.apple.security.cs.allow-unsigned-executable-memory",
            "com.apple.security.cs.disable-library-validation",
        )
        val application = packagingFile("macos/kani.entitlements")
        val runtime = packagingFile("macos/kani-runtime.entitlements")

        for (entitlement in required) {
            assertTrue(
                "the app bundle needs $entitlement under the hardened runtime",
                application.contains(entitlement),
            )
            assertTrue(
                "the embedded runtime needs $entitlement",
                runtime.contains(entitlement),
            )
        }
        // The launcher, not the runtime, sets the JVM's DYLD variables.
        assertTrue(
            "the app launcher sets JVM environment variables",
            application.contains("com.apple.security.cs.allow-dyld-environment-variables"),
        )

        // Kani is not sandboxed. A sandbox entitlement without `app-sandbox` has no
        // effect at all, so listing one would read as though Kani's loopback AnkiConnect
        // access or its file picker had been granted here, when they work because the
        // process is unsandboxed. The narrower runtime file must also stay narrower:
        // making the two identical widens the signed surface for no gain, invisibly.
        for (file in listOf(application, runtime)) {
            assertTrue(
                "Kani is not sandboxed; app-sandbox must not be claimed",
                !file.contains("<key>com.apple.security.app-sandbox</key>"),
            )
            assertTrue(
                "a sandbox network entitlement without app-sandbox does nothing",
                !file.contains("<key>com.apple.security.network.client</key>"),
            )
        }
        val runtimeHasDyld =
            runtime.contains("<key>com.apple.security.cs.allow-dyld-environment-variables</key>")
        assertTrue(
            "the embedded runtime must stay narrower than the application",
            !runtimeHasDyld,
        )
    }

    @Test
    fun everyInstallerCarriesTheSameCopyrightLine() {
        val convention = conventionSource()

        assertEquals("Copyright (c) bee-san", KaniDesktopIdentity.COPYRIGHT)
        assertTrue(
            "the distribution must set a copyright line for all three installers",
            convention.contains("copyright = KaniDesktopIdentity.COPYRIGHT"),
        )
    }

    private fun repositoryRoot(): File =
        File(requireNotNull(System.getProperty("kani.repositoryRoot")))

    private fun conventionSource(): String = File(
        repositoryRoot(),
        "build-logic/src/main/kotlin/" +
            "kani.desktop-application-conventions.gradle.kts",
    ).readText()

    private fun packagingFile(relativePath: String): String = File(
        repositoryRoot(),
        "desktop-app/${KaniDesktopIdentity.PACKAGING_DIRECTORY}/$relativePath",
    ).readText()
}
