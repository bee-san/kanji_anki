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
    fun smokeLaunchForcesTheDeterministicSoftwareRendererOnly() {
        val properties = mutableMapOf<String, String>()
        configureSmokeRenderer(
            DesktopLaunchOptions(smokeTest = true, temporaryData = true),
            properties::put,
        )
        assertEquals(
            mapOf(SMOKE_RENDER_API_PROPERTY to SMOKE_RENDER_API),
            properties,
        )

        properties.clear()
        configureSmokeRenderer(
            DesktopLaunchOptions(smokeTest = false, temporaryData = false),
            properties::put,
        )
        assertTrue(properties.isEmpty())
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
                smokeReadyReporter = { selectedResultFile ->
                    assertFalse(Files.exists(temporaryRoot))
                    assertEquals(resultFile, selectedResultFile)
                    reportSmokeReady(selectedResultFile)
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
                smokeReadyReporter = {
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
                smokeReadyReporter = {
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
