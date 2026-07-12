plugins {
    id("kani.kotlin-library-conventions")
}

kaniLibrary {
    // Kotlin-generated when-mapping synthetic classes are not directly testable.
    coverageExcludes.add("**/*WhenMappings*")
}

dependencies {
    api(kotlin("stdlib"))
    implementation(project(":domain"))
}
