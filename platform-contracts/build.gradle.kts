plugins {
    id("kani.kotlin-library-conventions")
}

kaniLibrary {
    // The interface default is exercised directly; Kotlin's compatibility
    // forwarding class contains no independent behavior.
    coverageExcludes.add("**/ReadingMediaSource\$DefaultImpls.class")
}
