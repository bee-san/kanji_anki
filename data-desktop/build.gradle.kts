plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data-sql"))
    implementation(project(":backup-core"))
    implementation(libs.androidx.sqlite.bundled)
    // `api`, not `implementation`: DesktopProfileRepositories hands a host the
    // five `:data-api` interfaces. `:data-sql` stays `implementation` on
    // purpose, so a consumer can hold an opened profile without being able to
    // name SqlDatabase or SchemaTransition.
    api(project(":data-api"))
    testImplementation(testFixtures(project(":data-sql")))
    testImplementation(libs.kotlinx.coroutines.test)
}
