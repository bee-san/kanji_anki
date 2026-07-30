plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data-api"))
    implementation(project(":dictionary-core"))
    implementation(project(":sync-api"))
    implementation(project(":sync-domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
