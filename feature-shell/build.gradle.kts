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
            // `api` for the same reason: the shell renders Home's surfaces and
            // its route callback hands a host the copy types they take, so a
            // host cannot supply a Home screen without seeing this module.
            api(project(":feature-home"))
            // `api` for the same reason as `:feature-home`: from Goal 195 the shell
            // aggregates Study's surfaces too, so a host composing the Study route
            // sees this module through the shell rather than depending on it directly.
            api(project(":feature-study"))
        }
    }
}
