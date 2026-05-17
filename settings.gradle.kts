pluginManagement {
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
include(":fsrs-java")
include(":fsrs")
include(":core")
include(":domain")
include(":dictionary-core")
include(":writing-core")
include(":designsystem")
include(":data")
include(":ankidroid")
include(":dictionary-android")
include(":writing-android")
include(":app")
