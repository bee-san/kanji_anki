plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        // `api`, not `implementation`: :ui-common renders presentation state, so
        // UiText and the destination types appear in its own public signatures.
        commonMain.dependencies {
            api(project(":presentation-api"))
        }
    }
}
