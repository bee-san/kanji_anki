import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    jacoco
}

android {
    namespace = "dev.bee.kanjianki.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required = true
        html.required = true
    }

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes").get().asFile) {
                exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
                exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/testDebugUnitTest.exec",
            )
        },
    )
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
}
