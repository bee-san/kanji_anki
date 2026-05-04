plugins {
    id("com.android.application")
}

import org.gradle.api.GradleException
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

fun intConfigValue(name: String, defaultValue: Int): Int =
    configValue(name)?.toIntOrNull() ?: defaultValue

val appVersionName = configValue("KANJI_ANKI_VERSION_NAME") ?: "0.3.0"
val appVersionCode = intConfigValue("KANJI_ANKI_VERSION_CODE", 3000)
val releaseOwner = configValue("KANJI_ANKI_RELEASE_OWNER") ?: "bee-san"
val releaseRepo = configValue("KANJI_ANKI_RELEASE_REPO") ?: "kanji_anki"

val signingStoreFile = configValue("KANJI_ANKI_SIGNING_STORE_FILE")
val signingStorePassword = configValue("KANJI_ANKI_SIGNING_STORE_PASSWORD")
val signingKeyAlias = configValue("KANJI_ANKI_SIGNING_KEY_ALIAS")
val signingKeyPassword = configValue("KANJI_ANKI_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }

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
        disable += "GradleDependency"
    }
}

tasks.matching { task ->
    task.name == "assembleRelease" || task.name == "bundleRelease"
}.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException("Release signing is required. Set KANJI_ANKI_SIGNING_STORE_FILE, KANJI_ANKI_SIGNING_STORE_PASSWORD, KANJI_ANKI_SIGNING_KEY_ALIAS, and KANJI_ANKI_SIGNING_KEY_PASSWORD.")
        }
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
