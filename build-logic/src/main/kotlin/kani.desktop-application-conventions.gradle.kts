import dev.bee.kanjianki.buildlogic.KaniDesktopIdentity
import dev.bee.kanjianki.buildlogic.KaniDesktopPackageVersions
import dev.bee.kanjianki.buildlogic.KaniDesktopRuntimeModules
import dev.bee.kanjianki.buildlogic.KaniPackagingJdk
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractCheckNativeDistributionRuntime
import org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask
import org.jetbrains.compose.desktop.application.tasks.AbstractSuggestModulesTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    jacoco
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("jvmTarget").get().requiredVersion.toInt()
val kaniVersionName = libs.findVersion("appVersionName").get().requiredVersion

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

/**
 * The JDK whose runtime `jpackage` ships inside the installed application.
 *
 * Compose's packaging default is the **Gradle daemon's** `java.home`, which neither the
 * `java { toolchain }` above nor the catalog `jvmTarget` constrains. Building on a
 * Temurin 21 daemon with no other change was verified to ship `JAVA_VERSION="21.0.11"`
 * inside the image, silently. So the packaging JVM is resolved explicitly — vendor and
 * feature version through Gradle's toolchain service, exact patch through
 * `KaniPackagingJdk.verify`, which a toolchain spec cannot express.
 */
val packagingJdkLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(KaniPackagingJdk.FEATURE_VERSION))
    vendor.set(JvmVendorSpec.ADOPTIUM)
}

/**
 * The verified packaging JDK home, resolved at execution time and not before.
 *
 * Laziness is the point: `:desktop-app:test` and `:desktop-app:check` must not require
 * the packaging JDK to be installed, only a packaging run must. The DSL's
 * `application.javaHome` is a plain `String`, so assigning it would resolve the toolchain
 * during configuration and make every task on this project need that JDK present.
 */
val packagingJdkHome: Provider<String> = providers.provider {
    // `installationPath` is the JDK home; `verify` reads its `release` file and fails with
    // the pinned download URL when it is not the exact pinned build.
    KaniPackagingJdk.verify(
        packagingJdkLauncher.get().metadata.installationPath.asFile,
    ).absolutePath
}

// Override the plugin's own wiring, which points every packaging tool at
// `application.javaHome` and so at the daemon's `java.home`.
//
// This must run inside an `afterEvaluate`, and the reason is ordering rather than style:
// the Compose plugin registers these tasks and sets their `javaHome` from its own
// `afterEvaluate`, so a `configureEach` added while this script is still evaluating is
// applied before the plugin's assignment and silently loses. That was not a theory — the
// first version of this commit did exactly that, and a `--rerun-tasks` build on a Temurin
// 21 daemon still shipped `JAVA_VERSION="21.0.11"`. This block is registered after the
// plugin's, so its assignment lands last.
//
// The tasks covered are `jlink` and `jpackage` (both `AbstractJvmToolOperationTask`), the
// module scan, and the runtime probe. The probe matters as much as the rest: its result is
// what the plugin believes the available module set to be.
afterEvaluate {
    tasks.withType<AbstractJvmToolOperationTask>().configureEach {
        javaHome.set(packagingJdkHome)
    }
    tasks.withType<AbstractSuggestModulesTask>().configureEach {
        javaHome.set(packagingJdkHome)
    }
    tasks.withType<AbstractCheckNativeDistributionRuntime>().configureEach {
        jdkHome.set(packagingJdkHome)
    }
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
            packageVersion = kaniVersionName
            description = KaniDesktopIdentity.DESCRIPTION
            vendor = KaniDesktopIdentity.VENDOR

            // The packaged runtime image is minimal, and a module missing from it
            // fails in the installed app rather than in this build. See
            // `KaniDesktopRuntimeModules` for how each entry earned its place.
            modules(*KaniDesktopRuntimeModules.REQUIRED.toTypedArray())

            // Each platform's version grammar is mapped by exactly one pinned
            // function, so the release tag remains the single source (Goal 202).
            macOS {
                bundleID = KaniDesktopIdentity.DESKTOP_ID
                packageVersion =
                    KaniDesktopPackageVersions.macOsJpackage(kaniVersionName)
                // The bundle build version is the monotonic Kani version code, which
                // is how macOS orders two builds sharing a short version.
                packageBuildVersion =
                    KaniDesktopPackageVersions.macOsBundleBuildVersion(kaniVersionName)
                iconFile.set(
                    project.file("${KaniDesktopIdentity.ICON_DIRECTORY}/kani.icns"),
                )
            }
            windows {
                upgradeUuid = KaniDesktopIdentity.WINDOWS_UPGRADE_UUID
                msiPackageVersion =
                    KaniDesktopPackageVersions.windowsMsi(kaniVersionName)
                iconFile.set(
                    project.file("${KaniDesktopIdentity.ICON_DIRECTORY}/kani.ico"),
                )
            }
            linux {
                debPackageVersion =
                    KaniDesktopPackageVersions.linuxDeb(kaniVersionName)
                iconFile.set(
                    project.file("${KaniDesktopIdentity.ICON_DIRECTORY}/kani.png"),
                )
            }
        }
    }
}
