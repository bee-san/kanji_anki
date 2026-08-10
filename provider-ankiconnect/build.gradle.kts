plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(project(":sync-api"))
    testImplementation(libs.kotlinx.coroutines.test)
    // The shared collection contract, so AnkiConnect is held to the same read
    // behavior as AnkiDroid rather than to a provider-local test of its own.
    testImplementation(testFixtures(project(":sync-api")))
}

/**
 * Forwards the live Anki Desktop qualification switches from the Gradle invocation
 * into the test JVM, so `LiveAnkiDesktopQualificationTest` can be turned on with
 * `-Pkani.liveAnkiDesktop=true` without a second mechanism.
 *
 * Absent means absent: unset properties are not forwarded at all, so the suite's
 * `assumeTrue` skips it and the deterministic gate never needs a live Anki. Passing
 * these through Gradle *properties* rather than reading the daemon's own system
 * properties keeps the opt-in explicit per invocation — a daemon reused from an
 * earlier live run cannot silently re-enable a suite that writes to a collection.
 */
tasks.test {
    for (name in listOf(
        "kani.liveAnkiDesktop",
        "kani.liveAnkiDesktopEndpoint",
        "kani.liveAnkiDesktopProfile",
    )) {
        val value = providers.gradleProperty(name).orNull ?: continue
        systemProperty(name, value)
        // A live run talks to a real process, so its result is not a function of
        // the inputs Gradle tracks.
        outputs.upToDateWhen { false }
    }
}
