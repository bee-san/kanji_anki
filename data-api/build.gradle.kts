plugins {
    id("kani.kotlin-library-conventions")
    id("java-test-fixtures")
}

dependencies {
    api(project(":core"))
    api(project(":sync-domain"))
    testFixturesImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
