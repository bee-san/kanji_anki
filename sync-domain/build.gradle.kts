plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(kotlin("stdlib"))
    implementation(project(":domain"))
}
