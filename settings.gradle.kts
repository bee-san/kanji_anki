pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kani"
include(":bee-fsrs")
include(":core")
include(":domain")
include(":sync-domain")
include(":writing-core")
include(":dictionary-core")
include(":update-core")
include(":reference-assets")
include(":progress-core")
include(":backup-core")
include(":platform-contracts")
include(":presentation-api")
include(":ui-common")
include(":feature-shell")
include(":feature-home")
include(":feature-study")
include(":feature-stats")
include(":feature-games")
include(":feature-missing-kanji")
include(":feature-settings")
include(":data-api")
include(":data-sql")
include(":sync-api")
include(":sync-engine")
include(":application")
include(":host-presentation")
include(":data-android")
include(":provider-ankidroid")
include(":platform-android")
include(":automation-android")
include(":app")
include(":data-desktop")
include(":provider-ankiconnect")
include(":platform-desktop")
include(":desktop-app")
