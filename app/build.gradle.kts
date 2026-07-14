plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

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

val appVersionName = renamedConfigValue("KANI_VERSION_NAME", "KANJI_ANKI_VERSION_NAME")
    ?: libs.versions.appVersionName.get()
val appVersionCode = renamedConfigValue("KANI_VERSION_CODE", "KANJI_ANKI_VERSION_CODE")?.toIntOrNull()
    ?: libs.versions.appVersionCode.get().toInt()
val releaseOwner = renamedConfigValue("KANI_RELEASE_OWNER", "KANJI_ANKI_RELEASE_OWNER") ?: "bee-san"
val releaseRepo = renamedConfigValue("KANI_RELEASE_REPO", "KANJI_ANKI_RELEASE_REPO") ?: "kanji_anki"

val signingStoreFile = renamedConfigValue("KANI_SIGNING_STORE_FILE", "KANJI_ANKI_SIGNING_STORE_FILE")
val signingStorePassword = renamedConfigValue("KANI_SIGNING_STORE_PASSWORD", "KANJI_ANKI_SIGNING_STORE_PASSWORD")
val signingKeyAlias = renamedConfigValue("KANI_SIGNING_KEY_ALIAS", "KANJI_ANKI_SIGNING_KEY_ALIAS")
val signingKeyPassword = renamedConfigValue("KANI_SIGNING_KEY_PASSWORD", "KANJI_ANKI_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }
val releaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName == "assembleRelease" || taskName.endsWith(":assembleRelease") ||
        taskName == "bundleRelease" || taskName.endsWith(":bundleRelease")
}

if (releaseBuildRequested && !hasReleaseSigning) {
    throw GradleException("Release signing is required. Set KANI_SIGNING_STORE_FILE, KANI_SIGNING_STORE_PASSWORD, KANI_SIGNING_KEY_ALIAS, and KANI_SIGNING_KEY_PASSWORD.")
}

android {
    namespace = "dev.bee.kanjianki"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.bee.kanjianki"
        minSdk = 26
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("GradleDependency", "OldTargetApi")
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

    implementation(project(":core"))
    implementation(project(":dictionary-core"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.wsc)
    implementation(libs.compose.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(project(":update-core"))
    implementation(project(":writing-core"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.mlkit.digital.ink)
    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    debugImplementation(libs.compose.ui.test.manifest)
}
