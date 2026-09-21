plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`, matching the other leaf features: this module's public
            // composables take presentation types (the portable analytics model,
            // UiTextResolver) and shared UI types in their signatures. The analytics
            // computation stays in :core/:application; the host maps its snapshot to
            // the portable model this module renders.
            api(project(":presentation-api"))
            api(project(":ui-common"))
        }
    }
}
