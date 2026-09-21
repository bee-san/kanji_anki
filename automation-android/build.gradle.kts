plugins {
    id("kani.android-library-conventions")
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.androidx.work.testing)
}
