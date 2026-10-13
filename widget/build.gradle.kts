plugins {
    id("kani.android-library-conventions")
    // Glance widgets are `@Composable`, so the compiler plugin is required — but deliberately
    // not `kani.android-compose-library-conventions`, which also pulls `ui-test-junit4` and
    // with it a transitive `activity-ktx:1.3.0` that has no recorded checksum. Adding one
    // would mean the three-host verification-metadata bootstrap for a module that renders no
    // Compose UI hierarchy to test against.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    // The Compose BOM pins Glance's transitive Compose artifacts to the versions the rest of
    // the repo already verifies. Without it Glance resolves its own much older ones (runtime
    // 1.1.1, ui-geometry 1.1.1, coroutines 1.5.x), none of which have recorded checksums.
    // Aligning versions is the fix; a trusted-artifact bypass is what
    // `docs/dependency-updates.md` forbids.
    implementation(platform(libs.compose.bom))

    // `:core` for the records and copy policies, `:data-api` for the read-only WidgetDataPort
    // the snapshot loaders render from, `:ui-common` for the shared palettes. Notably absent
    // is `:app`: the composition root depends on this module and not the reverse, which is
    // the whole point of the extraction.
    implementation(project(":core"))
    implementation(project(":data-api"))
    implementation(project(":platform-contracts"))
    implementation(project(":ui-common"))
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.androidx.glance.appwidget.testing)
}
