import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("kani.android-library-conventions")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.findLibrary("compose-bom").get())
    add("implementation", composeBom)
    add("implementation", libs.findLibrary("compose-foundation").get())
    add("implementation", libs.findLibrary("compose-material3").get())
    add("implementation", libs.findLibrary("compose-ui").get())
    add("testImplementation", composeBom)
    add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
    add("androidTestImplementation", composeBom)
    add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
}
