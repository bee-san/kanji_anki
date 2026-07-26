package dev.bee.kanjianki.buildlogic

import java.io.File
import java.util.Properties
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopAndMultiplatformConventionsFunctionalTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(timeout = 600_000L)
    fun desktopConventionCompilesTestsAndPublishesCoverage() {
        val projectDir = prepareFixtureProject("desktop-application", needsAndroidSdk = false)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlin",
                "test",
                "jacocoTestReport",
                "tasks",
                "--all",
                "--no-build-cache",
                "--dependency-verification=strict",
                "--stacktrace",
            )
            .build()

        assertSuccessful(result.task(":compileKotlin"), ":compileKotlin")
        assertSuccessful(result.task(":test"), ":test")
        assertSuccessful(result.task(":jacocoTestReport"), ":jacocoTestReport")
        for (taskName in REQUIRED_DESKTOP_APPLICATION_TASK_NAMES) {
            assertTrue("$taskName was not registered", result.output.contains(taskName))
        }
        assertTrue(
            File(
                projectDir,
                "build/reports/jacoco/test/jacocoTestReport.xml",
            ).let { it.isFile && it.length() > 0L },
        )
    }

    @Test(timeout = 600_000L)
    fun desktopConventionRejectsKotlinWarnings() {
        val projectDir = prepareFixtureProject(
            "desktop-application",
            needsAndroidSdk = false,
        )
        writeWarningSource(
            projectDir,
            "src/main/kotlin/dev/bee/kanjianki/desktop/conventions/fixture/" +
                "DesktopWarningFixture.kt",
            "dev.bee.kanjianki.desktop.conventions.fixture",
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlin",
                "--no-build-cache",
                "--dependency-verification=strict",
                "--stacktrace",
            )
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlin")?.outcome)
        assertWarningFailure(result.output)
    }

    @Test(timeout = 600_000L)
    fun multiplatformConventionCompilesEveryConfiguredSurface() {
        val projectDir = prepareFixtureProject(
            "multiplatform-compose-library",
            needsAndroidSdk = true,
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlinDesktop",
                "desktopTest",
                "compileAndroidMain",
                "testAndroidHostTest",
                "compileAndroidDeviceTest",
                "jacocoDesktopTestReport",
                "jacocoDesktopTestCoverageVerification",
                "--no-build-cache",
                "--dependency-verification=strict",
                "--stacktrace",
            )
            .build()

        for (task in REQUIRED_MULTIPLATFORM_TASKS) {
            assertSuccessful(result.task(task), task)
        }
        assertTrue(
            File(
                projectDir,
                "build/reports/jacoco/jacocoDesktopTestReport/" +
                    "jacocoDesktopTestReport.xml",
            ).let { it.isFile && it.length() > 0L },
        )
        assertTrue(
            File(
                projectDir,
                "build/classes/kotlin/desktop/main/dev/bee/kanjianki/" +
                    "multiplatform/compose/library/conventions/fixture/" +
                    "SharedFixtureKt.class",
            ).isFile,
        )
    }

    @Test(timeout = 600_000L)
    fun multiplatformConventionRejectsDesktopKotlinWarnings() {
        val projectDir = prepareFixtureProject(
            "multiplatform-compose-library",
            needsAndroidSdk = true,
        )
        writeWarningSource(
            projectDir,
            "src/desktopMain/kotlin/dev/bee/kanjianki/multiplatform/compose/" +
                "library/conventions/fixture/DesktopWarningFixture.kt",
            "dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture",
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlinDesktop",
                "--no-build-cache",
                "--dependency-verification=strict",
                "--stacktrace",
            )
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlinDesktop")?.outcome)
        assertWarningFailure(result.output)
    }

    @Test(timeout = 600_000L)
    fun multiplatformConventionRejectsAndroidKotlinWarnings() {
        val projectDir = prepareFixtureProject(
            "multiplatform-compose-library",
            needsAndroidSdk = true,
        )
        writeWarningSource(
            projectDir,
            "src/androidMain/kotlin/dev/bee/kanjianki/multiplatform/compose/" +
                "library/conventions/fixture/AndroidWarningFixture.kt",
            "dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture",
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileAndroidMain",
                "--no-build-cache",
                "--dependency-verification=strict",
                "--stacktrace",
            )
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":compileAndroidMain")?.outcome)
        assertWarningFailure(result.output)
    }

    @Test
    fun conventionsKeepDesktopJvmAndSharedAndroidKmpOwnershipSeparate() {
        val repositoryRoot = repositoryRoot()
        val desktopConvention = File(
            repositoryRoot,
            "build-logic/src/main/kotlin/kani.desktop-application-conventions.gradle.kts",
        ).readText()
        val sharedConvention = File(
            repositoryRoot,
            "build-logic/src/main/kotlin/" +
                "kani.multiplatform-compose-library-conventions.gradle.kts",
        ).readText()

        assertTrue(desktopConvention.contains("id(\"org.jetbrains.kotlin.jvm\")"))
        assertFalse(desktopConvention.contains("org.jetbrains.kotlin.multiplatform"))
        assertFalse(desktopConvention.contains("com.android."))

        for (pluginId in REQUIRED_SHARED_PLUGIN_IDS) {
            assertTrue("$pluginId must be owned by the shared convention", sharedConvention.contains(pluginId))
        }
        assertTrue(sharedConvention.contains("android {"))
        assertTrue(sharedConvention.contains("withHostTest"))
        assertTrue(sharedConvention.contains("withDeviceTest"))
        assertTrue(sharedConvention.contains("sourceSetTreeName = \"test\""))
        assertTrue(sharedConvention.contains("androidResources"))
        assertTrue(sharedConvention.contains("jvm(\"desktop\")"))
        assertFalse(sharedConvention.contains("androidTarget("))
        assertFalse(sharedConvention.contains("androidLibrary {"))
        assertFalse(sharedConvention.contains("id(\"com.android.library\")"))
        assertFalse(sharedConvention.contains("id(\"com.android.application\")"))
    }

    private fun prepareFixtureProject(folderName: String, needsAndroidSdk: Boolean): File {
        val repositoryRoot = repositoryRoot()
        val fixture = File(repositoryRoot, "build-logic/src/test/fixtures/$folderName")
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
        if (needsAndroidSdk) {
            File(projectDir, "local.properties").writeText(
                "sdk.dir=${androidSdk(repositoryRoot).invariantSeparatorsPath}\n",
            )
        }
        return projectDir
    }

    private fun androidSdk(repositoryRoot: File): File {
        val localSdk = Properties().apply {
            val localProperties = File(repositoryRoot, "local.properties")
            if (localProperties.isFile) {
                localProperties.inputStream().use(::load)
            }
        }.getProperty("sdk.dir")
        return requireNotNull(
            listOfNotNull(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                localSdk,
                "/tmp/android-sdk",
            ).map(::File).firstOrNull(File::isDirectory),
        ) {
            "Multiplatform convention fixture requires ANDROID_HOME or ANDROID_SDK_ROOT"
        }
    }

    private fun repositoryRoot(): File =
        File(requireNotNull(System.getProperty("kani.repositoryRoot")))

    private fun writeWarningSource(projectDir: File, path: String, packageName: String) {
        val source = File(projectDir, path)
        check(source.parentFile.mkdirs() || source.parentFile.isDirectory)
        source.writeText(
            """
            package $packageName

            fun warningAsError(value: String): Int = value!!.length
            """.trimIndent() + "\n",
        )
    }

    private fun assertWarningFailure(output: String) {
        assertTrue(output.contains("warnings found and -Werror specified"))
        assertTrue(output.contains("Unnecessary non-null assertion"))
    }

    private fun assertSuccessful(task: BuildTask?, path: String) {
        assertNotNull("$path was not present in the fixture build", task)
        val outcome = requireNotNull(task).outcome
        assertTrue(
            "$path did not complete successfully: $outcome",
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
        )
    }

    private companion object {
        val REQUIRED_MULTIPLATFORM_TASKS = listOf(
            ":compileKotlinDesktop",
            ":desktopTest",
            ":compileAndroidMain",
            ":testAndroidHostTest",
            ":compileAndroidDeviceTest",
            ":jacocoDesktopTestReport",
            ":jacocoDesktopTestCoverageVerification",
        )

        val REQUIRED_DESKTOP_APPLICATION_TASK_NAMES = listOf(
            "createDistributable",
            "packageDistributionForCurrentOS",
            "packageUberJarForCurrentOS",
            "run",
        )

        val REQUIRED_SHARED_PLUGIN_IDS = listOf(
            "org.jetbrains.kotlin.multiplatform",
            "com.android.kotlin.multiplatform.library",
            "org.jetbrains.compose",
            "org.jetbrains.kotlin.plugin.compose",
        )
    }
}
