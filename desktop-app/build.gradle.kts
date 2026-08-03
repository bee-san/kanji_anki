plugins {
    id("kani.desktop-application-conventions")
}

dependencies {
    // The five edges of the desktop composition root, and the only place they meet.
    // `:application` owns the container and startup contracts, `:data-desktop` opens
    // the profile, `:provider-ankiconnect` reaches Anki, `:platform-desktop` supplies
    // the platform ports, and `:feature-shell` renders. No other module may see more
    // than one of them; that is what makes this the composition root.
    implementation(project(":application"))
    implementation(project(":data-desktop"))
    implementation(project(":feature-shell"))
    implementation(project(":platform-desktop"))
    implementation(project(":progress-core"))
    implementation(project(":provider-ankiconnect"))
}
