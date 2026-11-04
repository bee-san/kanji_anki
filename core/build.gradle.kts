plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(project(":dictionary-core"))
    api(project(":domain"))
    api(project(":sync-domain"))
    implementation(project(":bee-fsrs"))
    implementation(project(":update-core"))
}
