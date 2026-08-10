plugins {
    id("kani.android-library-conventions")
}

dependencies {
    implementation(project(":sync-api"))
    implementation(libs.androidx.annotation)
    androidTestImplementation(testFixtures(project(":sync-api")))
}
