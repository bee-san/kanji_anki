import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.library)
    jacoco
}

android {
    namespace = "dev.bee.kanjianki.ankidroid"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    testImplementation(libs.coroutines.core)
    testImplementation(libs.junit)
}
