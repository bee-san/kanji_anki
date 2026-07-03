plugins {
    `kotlin-dsl`
}

dependencies {
    // Make the Kotlin JVM plugin available to precompiled script plugins so the
    // convention can apply org.jetbrains.kotlin.jvm.
    implementation(libs.kotlin.gradle.plugin)
    // Expose the generated version-catalog accessor (LibrariesForLibs) to the
    // precompiled script plugins so they can reference libs.* just like build files.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
