plugins {
    id("kani.android-library-conventions")
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(project(":writing-core"))
    implementation(libs.mlkit.digital.ink)
}
