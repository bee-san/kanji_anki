plugins {
    id("com.android.application")
    jacoco
}

import org.gradle.api.GradleException
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

val appVersionName = renamedConfigValue("KANI_VERSION_NAME", "KANJI_ANKI_VERSION_NAME") ?: "0.4.33"
val appVersionCode = renamedConfigValue("KANI_VERSION_CODE", "KANJI_ANKI_VERSION_CODE")?.toIntOrNull() ?: 4033
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testCoverage {
        jacocoVersion = "0.8.14"
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
    toolVersion = "0.8.14"
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required = true
        html.required = true
    }

    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes").get().asFile) {
            exclude(
                "**/R.class",
                "**/R$*.class",
                "**/BuildConfig.*",
                "**/Manifest*.*",
            )
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
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
    implementation(project(":core"))
    implementation(project(":dictionary-core"))
    implementation(project(":update-core"))
    implementation(project(":writing-core"))
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    testImplementation("junit:junit:${providers.gradleProperty("junitVersion").get()}")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
