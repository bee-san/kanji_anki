package dev.bee.kanjianki.buildlogic

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinLibraryConventionsFunctionalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(timeout = 300_000L)
    fun kotlinLibraryConventionCompilesCleanSource() {
        val projectDir = prepareFixtureProject("kotlin-library")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlin",
                "--no-build-cache",
                "--stacktrace",
            )
            .build()

        val outcome = result.task(":compileKotlin")?.outcome
        assertTrue(
            ":compileKotlin did not complete successfully: $outcome",
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
        )
    }

    @Test(timeout = 300_000L)
    fun kotlinLibraryWarningsFailCompilation() {
        val projectDir = prepareFixtureProject("kotlin-library-warning")
        val warningSource = File(
            projectDir,
            "src/main/kotlin/dev/bee/kanjianki/kotlin/library/conventions/" +
                "fixture/WarningAsErrorFixture.kt",
        )
        check(warningSource.parentFile.mkdirs() || warningSource.parentFile.isDirectory)
        warningSource.writeText(
            """
            package dev.bee.kanjianki.kotlin.library.conventions.fixture

            fun warningAsError(value: String): Int = value!!.length
            """.trimIndent() + "\n",
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlin",
                "--no-build-cache",
                "--stacktrace",
            )
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlin")?.outcome)
        assertTrue(result.output.contains("warnings found and -Werror specified"))
        assertTrue(result.output.contains("Unnecessary non-null assertion"))
    }

    private fun prepareFixtureProject(folderName: String): File {
        val repositoryRoot = File(requireNotNull(System.getProperty("kani.repositoryRoot")))
        val fixture = File(
            repositoryRoot,
            "build-logic/src/test/fixtures/kotlin-library",
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
        return projectDir
    }
}
