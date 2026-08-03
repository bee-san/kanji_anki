plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`, matching the other leaf features. The collection scan,
            // dictionary eligibility, and Anki/CSV writing stay in :core/:application;
            // the host maps its scan report and operation results to the portable
            // models this module renders.
            api(project(":presentation-api"))
            api(project(":ui-common"))
        }
    }
}
