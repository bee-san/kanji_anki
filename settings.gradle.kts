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
include(":backup-core")
include(":platform-contracts")
include(":data-api")
include(":data-sql")
include(":sync-api")
include(":sync-engine")
include(":application")
include(":data-android")
include(":provider-ankidroid")
include(":platform-android")
include(":automation-android")
include(":app")
include(":desktop-app")
