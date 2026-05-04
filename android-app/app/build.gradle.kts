plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

import org.gradle.api.GradleException
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

fun configValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull
            ?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

fun intConfigValue(name: String, defaultValue: Int): Int {
    val raw = configValue(name) ?: return defaultValue
    return raw.toIntOrNull()
        ?: throw GradleException("$name must be an integer, found '$raw'.")
}

val appVersionName = configValue("KANJI_ANKI_VERSION_NAME") ?: "0.1.0"
val appVersionCode = intConfigValue("KANJI_ANKI_VERSION_CODE", 1)
val githubReleaseOwner = configValue("KANJI_ANKI_RELEASE_OWNER") ?: "bee-san"
val githubReleaseRepo = configValue("KANJI_ANKI_RELEASE_REPO") ?: "kanji_anki"
val githubReleaseApkName = configValue("KANJI_ANKI_RELEASE_APK_NAME") ?: ""
val releaseSigningStoreFile = configValue("KANJI_ANKI_SIGNING_STORE_FILE")
val releaseSigningStorePassword = configValue("KANJI_ANKI_SIGNING_STORE_PASSWORD")
val releaseSigningKeyAlias = configValue("KANJI_ANKI_SIGNING_KEY_ALIAS")
val releaseSigningKeyPassword = configValue("KANJI_ANKI_SIGNING_KEY_PASSWORD")
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
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_RELEASE_OWNER", quoted(githubReleaseOwner))
        buildConfigField("String", "GITHUB_RELEASE_REPO", quoted(githubReleaseRepo))
        buildConfigField("String", "GITHUB_RELEASE_APK_NAME", quoted(githubReleaseApkName))
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Dependency upgrades are managed intentionally after device validation,
        // so the release gate focuses on real product issues instead of version drift.
        disable += "GradleDependency"
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
