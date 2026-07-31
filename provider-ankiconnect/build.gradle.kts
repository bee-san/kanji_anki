plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(project(":sync-api"))
    testImplementation(libs.kotlinx.coroutines.test)
    // The shared collection contract, so AnkiConnect is held to the same read
    // behavior as AnkiDroid rather than to a provider-local test of its own.
    testImplementation(testFixtures(project(":sync-api")))
}
