import org.gradle.accessors.dm.LibrariesForLibs

// Shared configuration for the pure-JVM Kotlin library modules (core, domain,
// sync-domain, dictionary-core, writing-core, update-core, fsrs-java): toolchain 17,
// JUnit4, and a JaCoCo report + 100% class-coverage verification wired into `check`.
// Modules add coverage exclusions via the `kaniLibrary { coverageExcludes.add(...) }`
// extension.

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

val libs = the<LibrariesForLibs>()

val conventionExtension = extensions.create("kaniLibrary", KaniLibraryConventionExtension::class.java)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coverageClassDirectories = files(
    fileTree(layout.buildDirectory.dir("classes/java/main").get().asFile) {
        conventionExtension.coverageExcludes.orNull?.forEach { exclude(it) }
    },
    fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
        conventionExtension.coverageExcludes.orNull?.forEach { exclude(it) }
    },
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(coverageClassDirectories)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(coverageClassDirectories)
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
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

dependencies {
    "testImplementation"(libs.junit)
}
