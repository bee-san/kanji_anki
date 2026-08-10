plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(project(":data-api"))
    api(project(":platform-contracts"))
    api(project(":sync-engine"))
    testImplementation(testFixtures(project(":data-api")))
    testImplementation(libs.kotlinx.coroutines.test)
}
