plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

dependencies {
    api(project(":fsrs-java"))
    testImplementation(libs.junit)
}
