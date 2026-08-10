import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

// A vendored checkout of dev.bee:bee-fsrs 0.2.0 from
// https://github.com/bee-san/bee-fsrs — see PROVENANCE.md.
//
// Do not edit src/ or testdata/ here. Change the engine upstream and re-vendor, or Kani
// silently forks the mathematics BeeCode also depends on; the whole point of the split is
// that both consumers resolve the same tested engine. FsrsVendoringTest asserts this copy
// is byte-identical to the version it claims to be.
//
// This file is deliberately the one exception, because it is Kani's build integration
// rather than upstream's: the module runs under kani.kotlin-library-conventions for the
// shared toolchain, JUnit 4, and the 100% class-coverage gate. Upstream's own
// build.gradle.kts is not vendored.
//
// jvmDefault = NO_COMPATIBILITY is upstream's `-Xjvm-default=all`, spelled the way this
// Kotlin version wants it. Interface methods on FsrsEngine/Fsrs7Engine must be real
// default methods, not DefaultImpls, so a Java caller sees a plain interface.
//
// Dependency-free apart from the Kotlin stdlib, with no clock, storage, or logging.

plugins {
    id("kani.kotlin-library-conventions")
}

kaniLibrary {
    // The `Fsrs7Engine` *interface* class file holds nothing but the two @JvmStatic
    // Java-interop bridges for `create`/`latestDefault`. Kotlin callers never reach
    // them: `Fsrs7Engine.create(...)` compiles to `Fsrs7Engine$Companion.create`,
    // which is covered, as is every line of `DefaultFsrs7Engine` where the
    // mathematics actually lives. The bridge is reachable only from Java, and Kani
    // has no Java caller.
    //
    // This is unreachable interop bytecode, not an untested code path, so the class
    // counter is the wrong instrument here rather than a gap to paper over. Note
    // that FSRS-6's `FsrsEngine` has the identical pair of uncovered bridges and
    // satisfies the gate only because it also carries one covered default method —
    // so the pre-vendoring module passed this rule by accident, not by coverage.
    //
    // Still required after Kani adopted FSRS-7. Adoption made `Fsrs7Engine.create`
    // a production call, but from Kotlin, so it resolves to the companion and leaves
    // the bridge untouched; and this gate measures :bee-fsrs's own tests regardless
    // of who calls in from :core. Re-checked by deleting this line and rerunning
    // `:bee-fsrs:check`, which still reported 0.96.
    //
    // Scoped to the one class file: `Fsrs7Engine$Companion` and `DefaultFsrs7Engine`
    // are still held to 100%.
    coverageExcludes.add("**/Fsrs7Engine.class")
}

kotlin {
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    api(kotlin("stdlib"))
}
