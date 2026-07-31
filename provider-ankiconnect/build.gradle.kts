plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(project(":sync-api"))
    testImplementation(libs.kotlinx.coroutines.test)
}
