import dev.bee.kanjianki.buildlogic.KaniDesktopIdentity
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    jacoco
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("jvmTarget").get().requiredVersion.toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    "implementation"(libs.findLibrary("compose-multiplatform-runtime").get())
    "implementation"(libs.findLibrary("compose-multiplatform-ui").get())
    "implementation"(libs.findLibrary("compose-multiplatform-foundation").get())
    "implementation"(libs.findLibrary("compose-multiplatform-material3").get())
    "implementation"(libs.findLibrary("compose-navigation").get())
    "implementation"(compose.desktop.currentOs)
    "testImplementation"(libs.findLibrary("junit").get())
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestReport"))
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

compose.desktop {
    application {
        mainClass = KaniDesktopIdentity.MAIN_CLASS
        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
            packageName = KaniDesktopIdentity.APPLICATION_NAME
            packageVersion = libs.findVersion("appVersionName").get().requiredVersion
            description = KaniDesktopIdentity.DESCRIPTION
            vendor = KaniDesktopIdentity.VENDOR

            macOS {
                bundleID = KaniDesktopIdentity.DESKTOP_ID
                iconFile.set(
                    project.file("${KaniDesktopIdentity.ICON_DIRECTORY}/kani.icns"),
                )
            }
            windows {
                upgradeUuid = KaniDesktopIdentity.WINDOWS_UPGRADE_UUID
                iconFile.set(
                    project.file("${KaniDesktopIdentity.ICON_DIRECTORY}/kani.ico"),
                )
            }
            linux {
                iconFile.set(
                    project.file("${KaniDesktopIdentity.ICON_DIRECTORY}/kani.png"),
                )
            }
        }
    }
}
