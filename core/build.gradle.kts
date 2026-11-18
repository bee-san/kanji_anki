plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(project(":dictionary-core"))
    api(project(":domain"))
    api(project(":sync-domain"))
    api(project(":rwkv"))
    testImplementation(project(":fsrs-java"))
    implementation(project(":update-core"))
}

tasks.withType<Test>().configureEach {
    // Scheduler timeline goldens and the RWKV memory-model tests run the real
    // SM92 weights, the same bytes the app bundles.
    val weights = rootProject.file("app/src/main/assets/rwkv/sm92/weights.f32.bin")
    systemProperty("kani.rwkv.weights", weights.absolutePath)
    inputs.file(weights)
    // `./gradlew :core:test -PupdateSchedulerGoldens` rewrites the scheduler
    // timeline goldens and parity snapshot from the current behavior.
    if (project.hasProperty("updateSchedulerGoldens")) {
        systemProperty(
            "kani.updateSchedulerGoldens",
            project.file("src/test/resources/dev/bee/kanjianki/core").absolutePath,
        )
        outputs.upToDateWhen { false }
    }
}
