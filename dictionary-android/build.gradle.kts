plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.bee.kanjianki.dictionary"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":dictionary-core"))
    implementation(project(":domain"))
}
