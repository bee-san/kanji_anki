package dev.bee.kanjianki.buildlogic

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
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
    }
}
