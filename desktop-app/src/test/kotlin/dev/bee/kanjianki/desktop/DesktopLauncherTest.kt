package dev.bee.kanjianki.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLauncherTest {
    @Test
    fun parsesNormalAndTemporarySmokeLaunches() {
        assertEquals(
            DesktopLaunchOptions(smokeTest = false, temporaryData = false),
            DesktopLaunchOptions.parse(emptyArray()),
        )
        assertEquals(
            DesktopLaunchOptions(smokeTest = true, temporaryData = true),
            DesktopLaunchOptions.parse(
                arrayOf("--smoke-test", "--temporary-data"),
            ),
        )
    }

    @Test
    fun rejectsUnknownDuplicateOrUnpairedArguments() {
        for (arguments in listOf(
            arrayOf("--unknown"),
            arrayOf("--smoke-test"),
            arrayOf("--temporary-data"),
            arrayOf("--smoke-test", "--smoke-test", "--temporary-data"),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                DesktopLaunchOptions.parse(arguments)
            }
        }
    }

    @Test
    fun smokeLaunchUsesAndDeletesOnlyItsTemporaryRoot() {
        var normalRootResolved = false
        val temporaryRoot = Files.createTempDirectory("kani-desktop-launcher-test-")

        runDesktop(
            options = DesktopLaunchOptions(
                smokeTest = true,
                temporaryData = true,
            ),
            normalDataRoot = {
                normalRootResolved = true
                Path.of("must-not-be-used")
            },
            temporaryDataRoot = { temporaryRoot },
            windowRunner = { dataRoot, smokeTest ->
                assertEquals(temporaryRoot, dataRoot)
                assertTrue(smokeTest)
                Files.writeString(dataRoot.resolve("fixture"), FOUNDATION_TITLE)
            },
        )

        assertFalse(normalRootResolved)
        assertFalse(Files.exists(temporaryRoot))
    }

    @Test
    fun normalLaunchRetainsItsProfileRoot() {
        val normalRoot = Files.createTempDirectory("kani-desktop-normal-test-")
        try {
            val session = selectDataSession(
                options = DesktopLaunchOptions(
                    smokeTest = false,
                    temporaryData = false,
                ),
                normalDataRoot = { normalRoot },
                temporaryDataRoot = {
                    throw AssertionError("temporary root must not be resolved")
                },
            )

            assertEquals(normalRoot, session.root)
            assertFalse(session.deleteAfterLaunch)
        } finally {
            normalRoot.toFile().deleteRecursively()
        }
    }
}
