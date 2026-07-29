plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kani.release-integrity")
    jacoco
}

import dev.bee.kanjianki.buildlogic.KaniReleaseIntegrityExtension
import dev.bee.kanjianki.buildlogic.KaniVersioning
import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.Properties

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun configValue(name: String): String? =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

fun renamedConfigValue(name: String, legacyName: String): String? =
    configValue(name) ?: configValue(legacyName)

// ProviderFactory.exec makes Git metadata an explicit configuration input, so
// the local-tag fallback remains compatible with Gradle's configuration cache.
// The provider is realized only if neither release-tag nor name metadata exists.
val latestReachableGitTag = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
    isIgnoreExitValue = true
}.standardOutput.asText.map(String::trim)

val resolvedAppVersion = try {
    KaniVersioning.resolve(
        releaseTag = providers.gradleProperty("kaniReleaseTag").orNull,
        versionNameOverride = renamedConfigValue("KANI_VERSION_NAME", "KANJI_ANKI_VERSION_NAME"),
        versionCodeOverride = renamedConfigValue("KANI_VERSION_CODE", "KANJI_ANKI_VERSION_CODE"),
        latestReachableGitTag = { latestReachableGitTag.orNull },
        fallbackVersionName = libs.versions.appVersionName.get(),
        fallbackVersionCode = libs.versions.appVersionCode.get(),
    )
} catch (error: IllegalArgumentException) {
    throw GradleException("Invalid Kani version metadata: ${error.message}", error)
}
val appVersionName = resolvedAppVersion.version.versionName
val appVersionCode = resolvedAppVersion.version.versionCode
val releaseOwner = renamedConfigValue("KANI_RELEASE_OWNER", "KANJI_ANKI_RELEASE_OWNER") ?: "bee-san"
val releaseRepo = renamedConfigValue("KANI_RELEASE_REPO", "KANJI_ANKI_RELEASE_REPO") ?: "kanji_anki"

val signingStoreFile = renamedConfigValue("KANI_SIGNING_STORE_FILE", "KANJI_ANKI_SIGNING_STORE_FILE")
val signingStorePassword = renamedConfigValue("KANI_SIGNING_STORE_PASSWORD", "KANJI_ANKI_SIGNING_STORE_PASSWORD")
val signingKeyAlias = renamedConfigValue("KANI_SIGNING_KEY_ALIAS", "KANJI_ANKI_SIGNING_KEY_ALIAS")
val signingKeyPassword = renamedConfigValue("KANI_SIGNING_KEY_PASSWORD", "KANJI_ANKI_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }

extensions.configure<KaniReleaseIntegrityExtension> {
    versionName.set(appVersionName)
    versionCode.set(appVersionCode)
    versionSource.set(resolvedAppVersion.source.machineName)
    signingStoreFileConfigured.set(!signingStoreFile.isNullOrBlank())
    if (!signingStoreFile.isNullOrBlank()) {
        signingStoreFilePath.set(file(signingStoreFile).absolutePath)
    }
    signingStorePasswordConfigured.set(!signingStorePassword.isNullOrBlank())
    signingKeyAliasConfigured.set(!signingKeyAlias.isNullOrBlank())
    signingKeyPasswordConfigured.set(!signingKeyPassword.isNullOrBlank())
}

android {
    namespace = "dev.bee.kanjianki"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.bee.kanjianki"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RELEASE_OWNER", quoted(releaseOwner))
        buildConfigField("String", "RELEASE_REPO", quoted(releaseRepo))
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(signingStoreFile))
                storePassword = requireNotNull(signingStorePassword)
                keyAlias = requireNotNull(signingKeyAlias)
                keyPassword = requireNotNull(signingKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }
        create("minifiedSmoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".smoke"
            versionNameSuffix = "-smoke"
            signingConfig = signingConfigs.getByName("debug")
            ndk {
                abiFilters.clear()
                abiFilters += "x86_64"
            }
        }
    }

    sourceSets.getByName("minifiedSmoke") {
        kotlin.directories.add("src/release/kotlin")
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("GradleDependency", "OldTargetApi", "ChromeOsAbiSupport")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test>().configureEach {
    // Robolectric unit tests memory-map the android-all resource bundle (FileMap)
    // per sandbox. The app suite is large (1000+ tests) and runs in a single
    // forked worker, so those mapped resources accumulate across the run and can
    // exhaust the default worker heap on CI runners
    // (java.lang.OutOfMemoryError at FileMap). Give the worker an explicit heap
    // and periodically restart it so the mapped memory is reclaimed mid-run.
    maxHeapSize = "2g"
    forkEvery = 100L
    extensions.configure<JacocoTaskExtension>("jacoco") {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required = true
        html.required = true
    }

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes").get().asFile) {
                exclude(
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                )
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
                exclude(
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                )
            },
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes").get().asFile) {
                exclude(
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                )
            }
        )
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/testDebugUnitTest.exec",
            )
        }
    )
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":application"))
    implementation(project(":automation-android"))
    implementation(project(":core"))
    implementation(project(":data-api"))
    implementation(project(":dictionary-core"))
    implementation(project(":platform-contracts"))
    implementation(project(":platform-android"))
    implementation(project(":provider-ankidroid"))
    implementation(project(":sync-api"))
    implementation(project(":sync-engine"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(project(":update-core"))
    implementation(project(":writing-core"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(testFixtures(project(":data-api")))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(testFixtures(project(":sync-api")))
    debugImplementation(libs.compose.ui.test.manifest)
}
