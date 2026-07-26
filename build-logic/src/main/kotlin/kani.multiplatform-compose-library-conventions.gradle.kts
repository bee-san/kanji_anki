import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    jacoco
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("jvmTarget").get().requiredVersion.toInt()
val androidTargetSdk = libs.findVersion("androidTargetSdk").get().requiredVersion.toInt()
val generatedPackageSegment = project.name.replace('-', '.')

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    android {
        namespace = "dev.bee.kanjianki.$generatedPackageSegment"
        compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
        androidResources {
            enable = true
        }
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
            enableCoverage = true
            targetSdk {
                version = release(androidTargetSdk)
            }
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            enableCoverage = true
            targetSdk {
                version = release(androidTargetSdk)
            }
        }
        testCoverage {
            jacocoVersion = libs.findVersion("jacoco").get().requiredVersion
        }
        lint {
            abortOnError = true
            warningsAsErrors = true
            disable += setOf("GradleDependency", "OldTargetApi")
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("compose-multiplatform-resources").get())
            implementation(libs.findLibrary("compose-multiplatform-runtime").get())
            implementation(libs.findLibrary("compose-multiplatform-ui").get())
            implementation(libs.findLibrary("compose-multiplatform-foundation").get())
            implementation(libs.findLibrary("compose-multiplatform-material3").get())
            implementation(libs.findLibrary("compose-navigation").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.findLibrary("robolectric").get())
            implementation(libs.findLibrary("androidx-test-core").get())
            implementation(libs.findLibrary("androidx-test-ext-junit").get())
        }
        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.findLibrary("androidx-test-core").get())
            implementation(libs.findLibrary("androidx-test-runner").get())
            implementation(libs.findLibrary("androidx-test-ext-junit").get())
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.bee.kanjianki.$generatedPackageSegment.generated.resources"
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

val desktopMainClasses = kotlin.targets
    .getByName("desktop")
    .compilations
    .getByName("main")
    .output
    .classesDirs
val generatedResourceExcludes = listOf(
    "**/generated/resources/**",
    "**/*Res.class",
    "**/*Res\$*.class",
)
val desktopCoverageClasses = desktopMainClasses.asFileTree.matching {
    generatedResourceExcludes.forEach(::exclude)
}

val jacocoDesktopTestReport = tasks.register<JacocoReport>("jacocoDesktopTestReport") {
    dependsOn(tasks.named("desktopTest"))
    executionData(layout.buildDirectory.file("jacoco/desktopTest.exec"))
    classDirectories.setFrom(desktopCoverageClasses)
    sourceDirectories.setFrom(
        layout.projectDirectory.dir("src/commonMain/kotlin"),
        layout.projectDirectory.dir("src/desktopMain/kotlin"),
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val jacocoDesktopTestCoverageVerification =
    tasks.register<JacocoCoverageVerification>("jacocoDesktopTestCoverageVerification") {
        dependsOn(tasks.named("desktopTest"))
        executionData(layout.buildDirectory.file("jacoco/desktopTest.exec"))
        classDirectories.setFrom(desktopCoverageClasses)
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
    dependsOn(jacocoDesktopTestReport, jacocoDesktopTestCoverageVerification)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
