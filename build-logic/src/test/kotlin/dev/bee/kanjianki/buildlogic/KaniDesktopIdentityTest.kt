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
     * a real installed-image launch can see. `java.net.http` is called out by name
     * because it is the entire desktop provider transport — without it Kani cannot
     * reach Anki at all.
     */
    @Test
    fun theRuntimeImageCarriesEveryModuleTheInstalledAppNeeds() {
        assertEquals(
            listOf("java.instrument", "java.net.http", "jdk.unsupported"),
            KaniDesktopRuntimeModules.REQUIRED,
        )
        assertTrue(
            "The AnkiConnect transport needs java.net.http in the packaged runtime",
            "java.net.http" in KaniDesktopRuntimeModules.REQUIRED,
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
}
