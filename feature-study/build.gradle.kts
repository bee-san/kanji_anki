plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`, matching `:feature-home`: this module's public composables
            // take presentation types (StudySession, StudyCard, UiTextResolver) and
            // shared UI types in their signatures, so a host cannot call them without
            // also seeing those. Nothing else — the scheduler, session state machine,
            // and rating semantics live in `:application`/`:core`/`:sync-engine` and
            // never cross into a leaf feature module; the host maps their output to
            // the portable models this module renders.
            api(project(":presentation-api"))
            api(project(":ui-common"))
        }
    }
}
