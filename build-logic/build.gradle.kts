plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("kaniReleaseIntegrity") {
            id = "kani.release-integrity"
            implementationClass = "dev.bee.kanjianki.buildlogic.KaniReleaseIntegrityPlugin"
        }
    }
}

dependencies {
    // Make convention-applied plugins available to precompiled script plugins.
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    // Expose the generated version-catalog accessor (LibrariesForLibs) to the
    // precompiled script plugins so they can reference libs.* just like build files.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    testImplementation(gradleTestKit())
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    systemProperty("kani.repositoryRoot", rootProject.projectDir.parentFile.absolutePath)
    inputs.dir(layout.projectDirectory.dir("src/test/fixtures"))
    inputs.file(rootProject.projectDir.parentFile.resolve("gradle/libs.versions.toml"))
    inputs.file(rootProject.projectDir.parentFile.resolve("gradle/verification-metadata.xml"))
}
