import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.library")
    jacoco
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val conventionExtension =
    extensions.create("kaniAndroidLibrary", KaniAndroidLibraryConventionExtension::class.java)
val javaVersion = JavaVersion.toVersion(
    libs.findVersion("jvmTarget").get().requiredVersion,
)

android {
    namespace = "dev.bee.kanjianki.${project.name.replace('-', '.')}"
    compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()

    defaultConfig {
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    testCoverage {
        jacocoVersion = libs.findVersion("jacoco").get().requiredVersion
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("GradleDependency", "OldTargetApi")
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Test>().configureEach {
    useJUnit()
    extensions.configure<JacocoTaskExtension>("jacoco") {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val generatedClassExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
)
val coverageClassDirectories = files(
    provider {
        val excludes = generatedClassExcludes +
            conventionExtension.coverageExcludes.getOrElse(emptyList())
        listOf(
            fileTree(
                layout.buildDirectory.dir(
                    "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
                ).get().asFile,
            ) {
                excludes.forEach(::exclude)
            },
            fileTree(
                layout.buildDirectory.dir(
                    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
                ).get().asFile,
            ) {
                excludes.forEach(::exclude)
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
                excludes.forEach(::exclude)
            },
        )
    },
)
val coverageExecutionData = fileTree(layout.buildDirectory.get().asFile) {
    include(
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        "jacoco/testDebugUnitTest.exec",
    )
}

val jacocoDebugUnitTestReport = tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClassDirectories)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(coverageExecutionData)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val jacocoDebugUnitTestCoverageVerification =
    tasks.register<JacocoCoverageVerification>("jacocoDebugUnitTestCoverageVerification") {
        dependsOn("testDebugUnitTest")
        classDirectories.setFrom(coverageClassDirectories)
        sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
        executionData.setFrom(coverageExecutionData)
        violationRules {
            rule {
                limit {
                    counter = "CLASS"
                    value = "COVEREDRATIO"
                    minimum = "1.00".toBigDecimal()
                }
            }
        }
    }

tasks.named("check") {
    dependsOn(jacocoDebugUnitTestReport, jacocoDebugUnitTestCoverageVerification)
}

dependencies {
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("robolectric").get())
    add("testImplementation", libs.findLibrary("androidx-test-core").get())
    add("testImplementation", libs.findLibrary("androidx-test-ext-junit").get())
    add("testImplementation", libs.findLibrary("androidx-test-runner").get())
    add("androidTestImplementation", libs.findLibrary("androidx-test-core").get())
    add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
    add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
}
