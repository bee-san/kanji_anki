import org.gradle.api.tasks.testing.Test

plugins {
    id("kani.android-library-conventions")
}

dependencies {
    implementation(project(":data-sql"))
    implementation(libs.androidx.annotation)
    testImplementation(testFixtures(project(":data-sql")))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(testFixtures(project(":data-sql")))
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "kani.goal178.resources",
        rootProject.file(
            "app/src/test/resources/dev/bee/kanjianki/fixtures/goal178",
        ).absolutePath,
    )
}
