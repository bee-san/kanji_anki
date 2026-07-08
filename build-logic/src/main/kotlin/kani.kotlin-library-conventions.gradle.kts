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

// Resolve the excludes lazily: the extension's coverageExcludes list is populated
// by the module build script, which runs AFTER this convention plugin is applied.
// Reading `.orNull` eagerly inside the fileTree action (as before) captured an
// empty list, so any `kaniLibrary { coverageExcludes.add(...) }` was a silent
// no-op. The provider defers the read until the class directories are queried at
// task-graph time, by which point the module script has run.
val coverageClassDirectories = files(
    provider {
        val excludes = conventionExtension.coverageExcludes.getOrElse(emptyList())
        listOf(
            fileTree(layout.buildDirectory.dir("classes/java/main").get().asFile) {
                excludes.forEach { exclude(it) }
            },
            fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
                excludes.forEach { exclude(it) }
            },
        )
    },
)

// Debug aid: prints the effective coverage-exclude globs after the module
// script has configured them (used by CoverageExcludesFunctionalTest). The
// provider is resolved into a config-cache-friendly value at execution time,
// without capturing the extension (a script object) in the task action.
val effectiveCoverageExcludes = provider { conventionExtension.coverageExcludes.getOrElse(emptyList()) }
tasks.register("printCoverageExcludes") {
    val excludes = effectiveCoverageExcludes
    doLast {
        excludes.get().forEach { println(it) }
    }
}

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
