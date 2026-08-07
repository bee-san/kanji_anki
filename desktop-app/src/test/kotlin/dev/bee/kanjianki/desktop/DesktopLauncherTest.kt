package dev.bee.kanjianki.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
    fun parsesAPinnedDataRootAndKeepsIt() {
        val pinned = Files.createTempDirectory("kani-pinned-profile-")
        try {
            val options = DesktopLaunchOptions.parse(
                arrayOf("--smoke-test", "--data-root=$pinned"),
            )

            assertEquals(
                DesktopLaunchOptions(smokeTest = true, temporaryData = false, dataRoot = pinned),
                options,
            )

            // The session must not delete it. This is the property the upgrade gate rests
            // on: a second image runs over the same profile and asks whether the first
            // run's data survived, which is only a real question if nothing cleaned up.
            val session = selectDataSession(
                options = options,
                normalDataRoot = { error("a pinned root must not fall back to the user profile") },
                temporaryDataRoot = { error("a pinned root must not create a throwaway one") },
            )
            assertEquals(pinned, session.root)
            assertFalse(session.deleteAfterLaunch)
        } finally {
            pinned.toFile().deleteRecursively()
        }
    }

    @Test
    fun theReadyMarkerStatesWhichDataModeRan() {
        val directory = Files.createTempDirectory("kani-marker-")
        try {
            val temporary = directory.resolve("temporary.txt")
            reportSmokeReady(temporary, temporaryData = true)
            assertEquals("$SMOKE_READY_LINE\n", Files.readString(temporary))

            // A retention gate reads this line to decide whether the run it just observed
            // could have retained anything. Reporting `temporary_data=true` for a pinned
            // profile would let it verify survival across a root the app had deleted.
            val pinned = directory.resolve("pinned.txt")
            reportSmokeReady(pinned, temporaryData = false)
            assertEquals("$SMOKE_READY_LINE_PINNED_DATA\n", Files.readString(pinned))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsAnUnusableOrConflictingDataRoot() {
        val pinned = Files.createTempDirectory("kani-pinned-reject-")
        try {
            for (arguments in listOf(
                // A pinned root outside smoke mode would point the *real* app at a
                // caller-supplied profile, which is a data-loss shape, not a test seam.
                arrayOf("--data-root=$pinned"),
                // Contradictory promises about what survives the run.
                arrayOf("--smoke-test", "--temporary-data", "--data-root=$pinned"),
                arrayOf("--smoke-test", "--data-root="),
                arrayOf("--smoke-test", "--data-root=relative/profile"),
                arrayOf("--smoke-test", "--data-root=$pinned/missing"),
                arrayOf("--smoke-test", "--data-root=$pinned", "--data-root=$pinned"),
            )) {
                assertThrows(
                    "expected ${arguments.joinToString(" ")} to be rejected",
                    IllegalArgumentException::class.java,
                ) { DesktopLaunchOptions.parse(arguments) }
            }
        } finally {
            pinned.toFile().deleteRecursively()
        }
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
    fun smokeLaunchForcesTheDeterministicSoftwareRendererOnLinuxOnly() {
        val properties = mutableMapOf<String, String>()
        configureSmokeRenderer(
            DesktopLaunchOptions(smokeTest = true, temporaryData = true),
            properties::put,
            osName = "Linux",
        )
        assertEquals(
            mapOf(SMOKE_RENDER_API_PROPERTY to LINUX_SMOKE_RENDER_API),
            properties,
        )

        for ((osName, smokeTest) in listOf(
            "Mac OS X" to true,
            "Windows 11" to true,
            "Linux" to false,
        )) {
            properties.clear()
            configureSmokeRenderer(
                DesktopLaunchOptions(
                    smokeTest = smokeTest,
                    temporaryData = smokeTest,
                ),
                properties::put,
                osName = osName,
            )
            assertTrue("$osName smokeTest=$smokeTest", properties.isEmpty())
        }
    }

    @Test
    fun smokeLaunchUsesAndDeletesOnlyItsTemporaryRoot() {
        var normalRootResolved = false
        var smokeReadyReported = false
        val temporaryRoot = Files.createTempDirectory("kani-desktop-launcher-test-")
        val resultDirectory = Files.createTempDirectory("kani-desktop-result-test-")
        val resultFile = resultDirectory.resolve("ready")

        try {
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
                    DesktopWindowResult.SMOKE_RENDERED
                },
                smokeResultFile = { resultFile },
                smokeReadyReporter = { selectedResultFile, temporaryData ->
                    assertFalse(Files.exists(temporaryRoot))
                    assertEquals(resultFile, selectedResultFile)
                    // A throwaway root reports as one; the retention gate rejects this line.
                    assertTrue(temporaryData)
                    reportSmokeReady(selectedResultFile, temporaryData)
                    smokeReadyReported = true
                },
            )

            assertFalse(normalRootResolved)
            assertFalse(Files.exists(temporaryRoot))
            assertTrue(smokeReadyReported)
            assertEquals(
                "$SMOKE_READY_LINE\n",
                Files.readString(resultFile),
            )
        } finally {
            resultDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun smokeLaunchRejectsWindowCloseBeforeRendering() {
        var smokeReadyReported = false
        val temporaryRoot = Files.createTempDirectory("kani-desktop-early-close-test-")

        val failure = assertThrows(IllegalStateException::class.java) {
            runDesktop(
                options = DesktopLaunchOptions(
                    smokeTest = true,
                    temporaryData = true,
                ),
                normalDataRoot = {
                    throw AssertionError("normal root must not be resolved")
                },
                temporaryDataRoot = { temporaryRoot },
                windowRunner = { _, _ ->
                    DesktopWindowResult.CLOSED
                },
                smokeResultFile = {
                    throw AssertionError("result file must not resolve")
                },
                smokeReadyReporter = { _, _ ->
                    smokeReadyReported = true
                },
            )
        }

        assertTrue(failure.message.orEmpty().contains("before rendering completed"))
        assertFalse(smokeReadyReported)
        assertFalse(Files.exists(temporaryRoot))
    }

    @Test
    fun normalLaunchRetainsItsProfileRoot() {
        val normalRoot = Files.createTempDirectory("kani-desktop-normal-test-")
        try {
            runDesktop(
                options = DesktopLaunchOptions(
                    smokeTest = false,
                    temporaryData = false,
                ),
                normalDataRoot = { normalRoot },
                temporaryDataRoot = {
                    throw AssertionError("temporary root must not be resolved")
                },
                windowRunner = { selectedRoot, smokeTest ->
                    assertEquals(normalRoot, selectedRoot)
                    assertFalse(smokeTest)
                    DesktopWindowResult.CLOSED
                },
                smokeResultFile = {
                    throw AssertionError("normal launch must not resolve a smoke result file")
                },
                smokeReadyReporter = { _, _ ->
                    throw AssertionError("normal launch must not report smoke readiness")
                },
            )

            assertTrue(Files.exists(normalRoot))
        } finally {
            normalRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun smokeResultPathMustBePresentAndNonBlank() {
        val missing = assertThrows(IllegalArgumentException::class.java) {
            smokeResultFileFromEnvironment(emptyMap())
        }
        assertTrue(
            missing.message.orEmpty()
                .contains(SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE),
        )

        val blank = assertThrows(IllegalArgumentException::class.java) {
            smokeResultFileFromEnvironment(
                mapOf(SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE to " "),
            )
        }
        assertTrue(blank.message.orEmpty().contains("must not be blank"))

        val relative = assertThrows(IllegalArgumentException::class.java) {
            smokeResultFileFromEnvironment(
                mapOf(SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE to "relative/ready"),
            )
        }
        assertTrue(relative.message.orEmpty().contains("absolute path"))
    }

    @Test
    fun defaultSmokeDataRootIsCreatedBesideTheResultFile() {
        val resultDirectory = Files.createTempDirectory("kani-desktop-isolation-test-")
        try {
            val resultFile = resultDirectory.resolve("ready")
            val dataRoot = createSmokeTemporaryDataRoot(
                mapOf(
                    SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE to
                        resultFile.toString(),
                ),
            )
            assertEquals(resultDirectory, dataRoot.parent)
            assertTrue(dataRoot.fileName.toString().startsWith("kani-desktop-smoke-"))
            assertFalse(Files.exists(resultFile))
            assertTrue(dataRoot.toFile().deleteRecursively())
        } finally {
            resultDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun smokeDataRootRejectsAResultWithoutAUsableParent() {
        val fileSystemRoot = requireNotNull(Path.of("").toAbsolutePath().root)
        val missingParentFailure = assertThrows(IllegalArgumentException::class.java) {
            createSmokeTemporaryDataRoot(
                mapOf(
                    SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE to fileSystemRoot.toString(),
                ),
            )
        }
        assertTrue(
            missingParentFailure.message.orEmpty().contains("parent directory"),
        )

        val resultDirectory = Files.createTempDirectory("kani-desktop-missing-parent-test-")
        try {
            val missingResultFile = resultDirectory.resolve("missing").resolve("ready")
            val absentDirectoryFailure = assertThrows(IllegalArgumentException::class.java) {
                createSmokeTemporaryDataRoot(
                    mapOf(
                        SMOKE_RESULT_FILE_ENVIRONMENT_VARIABLE to
                            missingResultFile.toString(),
                    ),
                )
            }
            assertTrue(
                absentDirectoryFailure.message.orEmpty().contains("does not exist"),
            )
        } finally {
            resultDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun defaultDataRootUsesTheUserHomeAndRequiresIt() {
        assertEquals(
            Path.of("test-user-home", ".kani"),
            defaultDesktopDataRoot("test-user-home"),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            defaultDesktopDataRoot(null)
        }
        assertTrue(failure.message.orEmpty().contains("user.home"))
    }

    @Test
    fun temporaryDataDeletionFailureIsNotSilentlyIgnored() {
        val failure = assertThrows(IllegalStateException::class.java) {
            deleteTemporaryDataRoot(Path.of("retained-smoke-data")) {
                false
            }
        }

        assertTrue(failure.message.orEmpty().contains("Failed to delete"))
    }

    @Test
    fun smokeReadinessNeverOverwritesAPreexistingResult() {
        val resultDirectory = Files.createTempDirectory("kani-desktop-result-existing-")
        val resultFile = resultDirectory.resolve("ready")
        Files.writeString(resultFile, "untrusted\n")
        try {
            assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
                reportSmokeReady(resultFile)
            }
            assertEquals("untrusted\n", Files.readString(resultFile))
        } finally {
            resultDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun smokeReadinessCreatesExactResultBytes() {
        val resultDirectory = Files.createTempDirectory("kani-desktop-result-exact-")
        val resultFile = resultDirectory.resolve("ready")
        try {
            reportSmokeReady(resultFile)
            assertEquals(
                "$SMOKE_READY_LINE\n",
                Files.readString(resultFile),
            )
            assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
                Files.writeString(
                    resultFile,
                    "replacement",
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            }
        } finally {
            resultDirectory.toFile().deleteRecursively()
        }
    }
}
