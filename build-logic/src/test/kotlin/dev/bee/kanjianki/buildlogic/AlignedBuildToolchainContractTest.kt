package dev.bee.kanjianki.buildlogic

import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignedBuildToolchainContractTest {
    private val repositoryRoot = File(requireNotNull(System.getProperty("kani.repositoryRoot")))

    @Test
    fun wrapperAndCatalogPinTheSupportedGoal166Toolchain() {
        val wrapper = Properties().apply {
            repositoryFile("gradle/wrapper/gradle-wrapper.properties")
                .inputStream()
                .use(::load)
        }
        assertEquals(
            "https://services.gradle.org/distributions/gradle-9.4.1-bin.zip",
            wrapper.getProperty("distributionUrl"),
        )
        assertEquals(
            "2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb",
            wrapper.getProperty("distributionSha256Sum"),
        )
        assertEquals("true", wrapper.getProperty("validateDistributionUrl"))
        assertEquals(
            "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c",
            sha256(repositoryFile("gradle/wrapper/gradle-wrapper.jar")),
        )

        val catalog = repositoryText("gradle/libs.versions.toml")
        assertEquals("9.1.0", catalogVersion(catalog, "agp"))
        assertEquals("2.4.10", catalogVersion(catalog, "kotlin"))
        assertEquals("1.11.1", catalogVersion(catalog, "composeMultiplatform"))
        assertEquals("1.11.0-alpha07", catalogVersion(catalog, "composeMultiplatformMaterial3"))
        assertEquals("2026.05.01", catalogVersion(catalog, "composeBom"))
        assertEquals("1.5.0-alpha17", catalogVersion(catalog, "composeMaterial3"))
        assertEquals("2.9.2", catalogVersion(catalog, "composeNavigation"))
        assertEquals("0.144.6", catalogVersion(catalog, "skiko"))

        assertCatalogLine(
            catalog,
            """android-gradle-plugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }""",
        )
        assertCatalogLine(
            catalog,
            """kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }""",
        )
        assertCatalogLine(
            catalog,
            """kotlin-compose-gradle-plugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-gradle-plugin = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "composeMultiplatform" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-multiplatform-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-multiplatform-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-multiplatform-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "composeMultiplatformMaterial3" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-multiplatform-desktop-linux-x64 = { module = "org.jetbrains.compose.desktop:desktop-jvm-linux-x64", version.ref = "composeMultiplatform" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "composeMaterial3" }""",
        )
        assertCatalogLine(
            catalog,
            """compose-navigation = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "composeNavigation" }""",
        )
        assertCatalogLine(
            catalog,
            """skiko = { module = "org.jetbrains.skiko:skiko", version.ref = "skiko" }""",
        )
        assertCatalogLine(
            catalog,
            """kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }""",
        )
        assertCatalogLine(
            catalog,
            """kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }""",
        )
        assertCatalogLine(
            catalog,
            """jetbrains-compose = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }""",
        )
        assertFalse(catalog.contains("module = \"androidx.navigation:navigation-compose\""))
    }

    @Test
    fun appUsesAgpBuiltInKotlinWithExplicitComposeCompilerOwnership() {
        val appBuild = repositoryText("app/build.gradle.kts")
        val pluginsBlock = appBuild.substringAfter("plugins {").substringBefore("\n}")

        assertTrue(pluginsBlock.contains("alias(libs.plugins.android.application)"))
        assertTrue(pluginsBlock.contains("alias(libs.plugins.kotlin.compose)"))
        assertTrue(appBuild.contains("intermediates/built_in_kotlinc/"))
        assertFalse(pluginsBlock.contains("libs.plugins.kotlin.android"))
        assertFalse(pluginsBlock.contains("org.jetbrains.kotlin.android"))
        assertFalse(pluginsBlock.contains("kotlin(\"android\")"))
        assertFalse(pluginsBlock.contains("kotlin('android')"))
        assertFalse(pluginsBlock.contains("kotlin-android"))
    }

    @Test
    fun buildDoesNotUseCompatibilityOptOutsOrWeakenDependencyVerification() {
        val buildConfiguration = listOf(
            "gradle.properties",
            "settings.gradle.kts",
            "build.gradle.kts",
            "app/build.gradle.kts",
            "build-logic/build.gradle.kts",
        ).joinToString("\n", transform = ::repositoryText)
        val forbiddenCompatibilityOptOuts = mapOf(
            "android.builtInKotlin=false" to
                Regex("""android\.builtInKotlin\s*=\s*false""", RegexOption.IGNORE_CASE),
            "android.newDsl=false" to
                Regex("""android\.newDsl\s*=\s*false""", RegexOption.IGNORE_CASE),
            "kotlin.mpp.androidGradlePluginCompatibility.nowarn=true" to
                Regex(
                    """kotlin\.mpp\.androidGradlePluginCompatibility\.nowarn\s*=\s*true""",
                    RegexOption.IGNORE_CASE,
                ),
            "suppressKotlinVersionCompatibilityCheck" to
                Regex("""suppressKotlinVersionCompatibilityCheck""", RegexOption.IGNORE_CASE),
        )
        forbiddenCompatibilityOptOuts.forEach { (name, pattern) ->
            assertFalse("Compatibility opt-out must remain absent: $name", pattern.containsMatchIn(buildConfiguration))
        }

        val verificationMetadata = repositoryText("gradle/verification-metadata.xml")
        assertTrue(verificationMetadata.contains("<verify-metadata>true</verify-metadata>"))
        assertFalse(verificationMetadata.contains("<verify-metadata>false</verify-metadata>"))
        assertFalse(verificationMetadata.contains("<trusted-artifacts>"))
        assertFalse(verificationMetadata.contains("<ignored-keys>"))

        val verificationWeakening = mapOf(
            "org.gradle.dependency.verification=off|lenient" to
                Regex(
                    """org\.gradle\.dependency\.verification\s*=\s*(off|lenient)""",
                    RegexOption.IGNORE_CASE,
                ),
            "--dependency-verification=off|lenient" to
                Regex(
                    """--dependency-verification(?:\s+|=)(off|lenient)""",
                    RegexOption.IGNORE_CASE,
                ),
        )
        verificationWeakening.forEach { (name, pattern) ->
            assertFalse(
                "Dependency verification weakening must remain absent: $name",
                pattern.containsMatchIn(buildConfiguration),
            )
        }
    }

    private fun repositoryFile(path: String): File = File(repositoryRoot, path)

    private fun repositoryText(path: String): String = repositoryFile(path).readText()

    private fun sha256(file: File): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))

    private fun catalogVersion(catalog: String, name: String): String {
        val match = Regex("""(?m)^${Regex.escape(name)}\s*=\s*"([^"]+)"\s*$""").find(catalog)
        return requireNotNull(match) { "Missing version catalog entry: $name" }.groupValues[1]
    }

    private fun assertCatalogLine(catalog: String, expected: String) {
        assertTrue(
            "Missing version catalog contract: $expected",
            catalog.lineSequence().any { it.trim() == expected },
        )
    }
}
