plugins {
    id("kani.android-library-conventions")
}

kaniAndroidLibrary {
    // ML Kit's obfuscated digital-ink backend fails JVM verification under
    // Robolectric before Kani code runs. Fake-backend tests cover orchestration;
    // connected Android tests exercise these two production bridge classes.
    coverageExcludes.add(
        "**/MlKitJapaneseWritingRecognizer\$GoogleRecognitionBackend*.class",
    )
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(project(":writing-core"))
    implementation(libs.mlkit.digital.ink)
}
