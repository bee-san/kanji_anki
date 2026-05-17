plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.bee.kanjianki.writing"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":writing-core"))
    implementation(project(":domain"))
    implementation(libs.mlkit.digital.ink)
}
