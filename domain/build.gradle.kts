plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":fsrs"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
}
