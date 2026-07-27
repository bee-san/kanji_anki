plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(project(":data-api"))
    api(project(":platform-contracts"))
    testImplementation(testFixtures(project(":data-api")))
}
