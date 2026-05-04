plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localOrGradleProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val githubReleaseOwner = providers.gradleProperty("KANJI_ANKI_RELEASE_OWNER").orElse("bee-san")
val githubReleaseRepo = providers.gradleProperty("KANJI_ANKI_RELEASE_REPO").orElse("kanji_anki")
val githubReleaseApkName = providers.gradleProperty("KANJI_ANKI_RELEASE_APK_NAME").orElse("")
val releaseSigningStoreFile = localOrGradleProperty("KANJI_ANKI_SIGNING_STORE_FILE")
val releaseSigningStorePassword = localOrGradleProperty("KANJI_ANKI_SIGNING_STORE_PASSWORD")
val releaseSigningKeyAlias = localOrGradleProperty("KANJI_ANKI_SIGNING_KEY_ALIAS")
val releaseSigningKeyPassword = localOrGradleProperty("KANJI_ANKI_SIGNING_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(
        releaseSigningStoreFile,
        releaseSigningStorePassword,
        releaseSigningKeyAlias,
        releaseSigningKeyPassword,
    ).all { !it.isNullOrBlank() }

android {
    namespace = "dev.bee.kanjianki"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bee.kanjianki"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_RELEASE_OWNER", quoted(githubReleaseOwner.get()))
        buildConfigField("String", "GITHUB_RELEASE_REPO", quoted(githubReleaseRepo.get()))
        buildConfigField("String", "GITHUB_RELEASE_APK_NAME", quoted(githubReleaseApkName.get()))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("../parity-fixtures")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningStoreFile))
                storePassword = requireNotNull(releaseSigningStorePassword)
                keyAlias = requireNotNull(releaseSigningKeyAlias)
                keyPassword = requireNotNull(releaseSigningKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
