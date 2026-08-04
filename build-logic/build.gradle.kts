plugins {
    `kotlin-dsl`
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
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
    implementation(libs.compose.gradle.plugin)
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
    inputs.file(rootProject.projectDir.parentFile.resolve("gradle/wrapper/gradle-wrapper.properties"))
    inputs.file(rootProject.projectDir.parentFile.resolve("gradle/wrapper/gradle-wrapper.jar"))
    inputs.file(rootProject.projectDir.parentFile.resolve("gradle.properties"))
    inputs.file(rootProject.projectDir.parentFile.resolve("settings.gradle.kts"))
    inputs.file(rootProject.projectDir.parentFile.resolve("build.gradle.kts"))
    inputs.file(rootProject.projectDir.parentFile.resolve("app/build.gradle.kts"))
    // The convention script and the packaging resources are read as *files* by the
    // desktop identity tests, not consumed as compiled code, so Gradle cannot infer
    // them. Without these an edit to an entitlements file leaves the test UP-TO-DATE
    // and the assertion about its content unrun.
    inputs.file(
        layout.projectDirectory.file(
            "src/main/kotlin/kani.desktop-application-conventions.gradle.kts",
        ),
    )
    inputs.dir(
        rootProject.projectDir.parentFile.resolve("desktop-app/src/main/packaging"),
    )
}
