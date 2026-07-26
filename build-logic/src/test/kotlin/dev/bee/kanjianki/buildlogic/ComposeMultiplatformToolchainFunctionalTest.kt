package dev.bee.kanjianki.buildlogic

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
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

        val resolvedCoordinates = result.output.lineSequence()
            .filter { it.startsWith(RESOLUTION_PREFIX) }
            .map { it.removePrefix(RESOLUTION_PREFIX) }
            .toSet()
        assertEquals(
            "The selected Compose JVM graph must not contain stale or parallel versions.",
            EXPECTED_COMPOSE_JVM_COORDINATES,
            resolvedCoordinates,
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

    private companion object {
        const val RESOLUTION_PREFIX = "compose-jvm-resolution="

        val EXPECTED_COMPOSE_JVM_COORDINATES = setOf(
            "org.jetbrains.androidx.navigation:navigation-common-desktop:2.9.2",
            "org.jetbrains.androidx.navigation:navigation-common:2.9.2",
            "org.jetbrains.androidx.navigation:navigation-compose-desktop:2.9.2",
            "org.jetbrains.androidx.navigation:navigation-compose:2.9.2",
            "org.jetbrains.androidx.navigation:navigation-runtime-desktop:2.9.2",
            "org.jetbrains.androidx.navigation:navigation-runtime:2.9.2",
            "org.jetbrains.compose.animation:animation-core-desktop:1.11.1",
            "org.jetbrains.compose.animation:animation-core:1.11.1",
            "org.jetbrains.compose.animation:animation-desktop:1.11.1",
            "org.jetbrains.compose.animation:animation:1.11.1",
            "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.11.1",
            "org.jetbrains.compose.desktop:desktop-jvm:1.11.1",
            "org.jetbrains.compose.desktop:desktop:1.11.1",
            "org.jetbrains.compose.foundation:foundation-desktop:1.11.1",
            "org.jetbrains.compose.foundation:foundation-layout-desktop:1.11.1",
            "org.jetbrains.compose.foundation:foundation-layout:1.11.1",
            "org.jetbrains.compose.foundation:foundation:1.11.1",
            "org.jetbrains.compose.material3:material3-desktop:1.11.0-alpha07",
            "org.jetbrains.compose.material3:material3:1.11.0-alpha07",
            "org.jetbrains.compose.material:material-desktop:1.11.1",
            "org.jetbrains.compose.material:material-ripple-desktop:1.11.1",
            "org.jetbrains.compose.material:material-ripple:1.11.1",
            "org.jetbrains.compose.material:material:1.11.1",
            "org.jetbrains.compose.runtime:runtime-desktop:1.11.1",
            "org.jetbrains.compose.runtime:runtime-saveable-desktop:1.11.1",
            "org.jetbrains.compose.runtime:runtime-saveable:1.11.1",
            "org.jetbrains.compose.runtime:runtime:1.11.1",
            "org.jetbrains.compose.ui:ui-backhandler-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-backhandler:1.11.1",
            "org.jetbrains.compose.ui:ui-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-geometry-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-geometry:1.11.1",
            "org.jetbrains.compose.ui:ui-graphics-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-graphics:1.11.1",
            "org.jetbrains.compose.ui:ui-text-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-text:1.11.1",
            "org.jetbrains.compose.ui:ui-tooling-preview-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-tooling-preview:1.11.1",
            "org.jetbrains.compose.ui:ui-unit-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-unit:1.11.1",
            "org.jetbrains.compose.ui:ui-util-desktop:1.11.1",
            "org.jetbrains.compose.ui:ui-util:1.11.1",
            "org.jetbrains.compose.ui:ui:1.11.1",
            "org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10",
            "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.144.6",
            "org.jetbrains.skiko:skiko-awt:0.144.6",
            "org.jetbrains.skiko:skiko:0.144.6",
        )
    }
}
