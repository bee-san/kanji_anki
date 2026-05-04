plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
