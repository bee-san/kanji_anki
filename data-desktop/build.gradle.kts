plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data-sql"))
    implementation(project(":backup-core"))
    testImplementation(testFixtures(project(":data-sql")))
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.kotlinx.coroutines.test)
}
