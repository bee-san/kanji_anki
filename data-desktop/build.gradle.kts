plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data-sql"))
    implementation(project(":backup-core"))
    implementation(libs.androidx.sqlite.bundled)
    testImplementation(project(":data-api"))
    testImplementation(testFixtures(project(":data-sql")))
    testImplementation(libs.kotlinx.coroutines.test)
}
