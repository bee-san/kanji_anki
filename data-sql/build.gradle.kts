plugins {
    id("kani.kotlin-library-conventions")
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data-api"))
    implementation(project(":dictionary-core"))
    implementation(project(":sync-api"))
    implementation(project(":sync-domain"))
    implementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.junit)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    systemProperty(
        "kani.goal178.resources",
        rootProject.file(
            "app/src/test/resources/dev/bee/kanjianki/fixtures/goal178",
        ).absolutePath,
    )
}
