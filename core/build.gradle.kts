plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(project(":dictionary-core"))
    api(project(":domain"))
    api(project(":sync-domain"))
    implementation(project(":fsrs-java"))
    implementation(project(":update-core"))
}
