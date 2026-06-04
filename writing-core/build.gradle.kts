plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

jacoco {
    toolVersion = "0.8.14"
}

val coverageClassDirectories = files(
    fileTree(layout.buildDirectory.dir("classes/java/main").get().asFile) {
        exclude("**/*WhenMappings*")
    },
    fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
        exclude("**/*WhenMappings*")
    },
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClassDirectories)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
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

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

dependencies {
    api(kotlin("stdlib"))
    testImplementation("junit:junit:${providers.gradleProperty("junitVersion").get()}")
}
