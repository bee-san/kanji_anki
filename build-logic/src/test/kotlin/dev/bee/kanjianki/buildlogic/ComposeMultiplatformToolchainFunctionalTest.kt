package dev.bee.kanjianki.buildlogic

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComposeMultiplatformToolchainFunctionalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(timeout = 300_000L)
    fun composeJvmFixtureCompilesAndResolvesThePinnedToolchain() {
        val repositoryRoot = File(requireNotNull(System.getProperty("kani.repositoryRoot")))
        val fixture = File(
            repositoryRoot,
            "build-logic/src/test/fixtures/compose-jvm",
        )
        val projectDir = temporaryFolder.newFolder("compose-jvm")
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

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "compileKotlin",
                "printComposeJvmResolution",
                "--no-build-cache",
                "--no-configuration-cache",
                "--dependency-verification=strict",
                "--stacktrace",
            )
            .build()

        assertSuccessful(result.task(":compileKotlin"), ":compileKotlin")
        assertSuccessful(
            result.task(":printComposeJvmResolution"),
            ":printComposeJvmResolution",
        )
        assertTrue(
            File(
                projectDir,
                "build/classes/kotlin/main/dev/bee/kanjianki/compose/jvm/fixture/" +
                    "ComposeJvmFixtureKt.class",
            ).isFile,
        )

        assertResolved(
            result.output,
            "org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10",
        )
        assertResolved(result.output, "org.jetbrains.compose.runtime:runtime:1.11.1")
        assertResolved(result.output, "org.jetbrains.compose.ui:ui:1.11.1")
        assertResolved(
            result.output,
            "org.jetbrains.compose.material3:material3:1.11.0-alpha07",
        )
        assertResolved(
            result.output,
            "org.jetbrains.androidx.navigation:navigation-compose:2.9.2",
        )
        assertResolved(result.output, "org.jetbrains.skiko:skiko:0.144.6")
        assertResolved(
            result.output,
            "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.11.1",
        )
        assertResolved(
            result.output,
            "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.144.6",
        )
    }

    private fun assertSuccessful(task: org.gradle.testkit.runner.BuildTask?, path: String) {
        assertNotNull("$path was not present in the fixture build", task)
        val outcome = requireNotNull(task).outcome
        assertTrue(
            "$path did not complete successfully: $outcome",
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
        )
    }

    private fun assertResolved(output: String, coordinate: String) {
        assertTrue(
            "Expected strict Compose JVM resolution to contain $coordinate.\n$output",
            output.contains("compose-jvm-resolution=$coordinate"),
        )
    }
}
