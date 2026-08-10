plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`, matching the other leaf features. The game engine
            // (KanjiGameEngine) stays in :core; the host maps its question/score/result
            // to the portable models this module renders.
            api(project(":presentation-api"))
            api(project(":ui-common"))
        }
    }
}
