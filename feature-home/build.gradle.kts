plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`, for the same reason `:feature-shell` uses `api`: this
            // module's public composables take presentation types (RouteState,
            // OnboardingPlan, UiTextResolver) and shared UI types in their
            // signatures, so a host cannot call them without also seeing those.
            api(project(":presentation-api"))
            api(project(":ui-common"))
            // Nothing else. Onboarding's state machine is in `:presentation-api`
            // precisely so this module needs no JVM dependency: `:core`'s
            // `HomeImportOnboardingPolicy` is plain-JVM and its copy is worded for
            // Android runtime permissions, neither of which crosses to here.
        }
    }
}
