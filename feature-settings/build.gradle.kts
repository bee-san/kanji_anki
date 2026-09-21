plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`, matching the other leaf features. Settings validation,
            // capability gating, and persistence stay in :core/:application; the host
            // maps its settings snapshot to the portable model this module renders.
            api(project(":presentation-api"))
            api(project(":ui-common"))
        }
    }
}
