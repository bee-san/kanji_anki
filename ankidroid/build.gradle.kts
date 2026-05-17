plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.bee.kanjianki.ankidroid"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":domain"))
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit)
}
