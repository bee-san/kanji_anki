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
include(":fsrs-java")
include(":core")
include(":domain")
include(":sync-domain")
include(":writing-core")
include(":dictionary-core")
include(":update-core")
include(":platform-contracts")
include(":data-api")
include(":app")
include(":desktop-app")
