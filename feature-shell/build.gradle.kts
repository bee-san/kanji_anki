plugins {
    id("kani.multiplatform-compose-library-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Both `api`: the shell's own signatures name presentation types
            // (ShellState, KaniAction, UiTextResolver) and shared UI types, so a
            // host that composes KaniShell cannot do so without them.
            api(project(":presentation-api"))
            api(project(":ui-common"))
        }
    }
}
