package dev.bee.kanjianki.buildlogic

import java.io.File
import java.util.Properties
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidLibraryConventionsFunctionalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(timeout = 300_000L)
    fun composeLibraryConventionCompilesLintsTestsAndPublishesCoverage() {
        val projectDir = prepareFixtureProject("android-library")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileDebugKotlin",
                "lintDebug",
                "testDebugUnitTest",
                "jacocoDebugUnitTestReport",
                "jacocoDebugUnitTestCoverageVerification",
                "--no-build-cache",
                "--stacktrace",
            )
            .build()

        for (task in REQUIRED_TASKS) {
            val executedTask = result.task(task)
            assertNotNull("$task was not present in the fixture build", executedTask)
            val outcome = requireNotNull(executedTask).outcome
            assertTrue(
                "$task did not complete successfully: $outcome",
                outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
            )
        }
        val coverageReport = File(
            projectDir,
            "build/reports/jacoco/jacocoDebugUnitTestReport/" +
                "jacocoDebugUnitTestReport.xml",
        )
        assertTrue(coverageReport.isFile && coverageReport.length() > 0L)
    }

    @Test(timeout = 300_000L)
    fun builtInAndroidKotlinWarningsFailCompilation() {
        val projectDir = prepareFixtureProject("android-library-warning")
        val warningSource = File(
            projectDir,
            "src/main/kotlin/dev/bee/kanjianki/android/compose/library/conventions/" +
                "fixture/WarningAsErrorFixture.kt",
        )
        check(warningSource.parentFile.mkdirs() || warningSource.parentFile.isDirectory)
        warningSource.writeText(
            """
            package dev.bee.kanjianki.android.compose.library.conventions.fixture

            fun warningAsError(value: String): Int = value!!.length
            """.trimIndent() + "\n",
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileDebugKotlin",
                "--no-build-cache",
                "--stacktrace",
            )
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":compileDebugKotlin")?.outcome)
        assertTrue(result.output.contains("warnings found and -Werror specified"))
        assertTrue(result.output.contains("Unnecessary non-null assertion"))
    }

    private fun prepareFixtureProject(folderName: String): File {
        val repositoryRoot = File(requireNotNull(System.getProperty("kani.repositoryRoot")))
        val fixture = File(
            repositoryRoot,
            "build-logic/src/test/fixtures/android-compose-library",
        )
        val projectDir = temporaryFolder.newFolder(folderName)
        fixture.copyRecursively(projectDir, overwrite = true)
        val fixtureGradleDirectory = File(projectDir, "gradle")
        check(fixtureGradleDirectory.mkdirs() || fixtureGradleDirectory.isDirectory)
        File(repositoryRoot, "gradle/libs.versions.toml").copyTo(
            File(fixtureGradleDirectory, "libs.versions.toml"),
            overwrite = true,
        )
        File(repositoryRoot, "gradle/verification-metadata.xml").copyTo(
            File(fixtureGradleDirectory, "verification-metadata.xml"),
            overwrite = true,
        )
        val localSdk = Properties().apply {
            val localProperties = File(repositoryRoot, "local.properties")
            if (localProperties.isFile) {
                localProperties.inputStream().use(::load)
            }
        }.getProperty("sdk.dir")
        val androidSdk = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            localSdk,
            "/tmp/android-sdk",
        ).map(::File).firstOrNull(File::isDirectory)
        requireNotNull(androidSdk) {
            "Android convention fixture requires ANDROID_HOME or ANDROID_SDK_ROOT"
        }
        File(projectDir, "local.properties").writeText(
            "sdk.dir=${androidSdk.invariantSeparatorsPath}\n",
        )
        return projectDir
    }

    private companion object {
        val REQUIRED_TASKS = listOf(
            ":compileDebugKotlin",
            ":lintDebug",
            ":testDebugUnitTest",
            ":jacocoDebugUnitTestReport",
            ":jacocoDebugUnitTestCoverageVerification",
        )
    }
}
