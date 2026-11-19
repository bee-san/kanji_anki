// SPDX-License-Identifier: AGPL-3.0-or-later
//
// Pure-Kotlin port of the small RWKV-Curve memory model (SM92) from
// Pedrubik2000/kuma3-anki-core. See RwkvAlgorithmInfo and
// THIRD_PARTY_NOTICES.md for provenance and licensing.

plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    api(kotlin("stdlib"))
}

tasks.withType<Test>().configureEach {
    // The parity test replays the published SM92 vectors and the bundled
    // weights; both live outside the module's resources so the APK asset and
    // the test read the exact same bytes.
    systemProperty("kani.rwkv.weights", rootProject.file("rwkv/weights/sm92/weights.f32.bin").absolutePath)
    systemProperty("kani.rwkv.vectors", project.file("testdata/sm92-vectors.json.gz").absolutePath)
    inputs.file(rootProject.file("rwkv/weights/sm92/weights.f32.bin"))
    inputs.file(project.file("testdata/sm92-vectors.json.gz"))
}
