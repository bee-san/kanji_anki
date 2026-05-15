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

val fsrsJavaPath = providers.gradleProperty("fsrsJavaPath").orNull ?: "../fsrs_java"
includeBuild(fsrsJavaPath)

include(":core")
include(":app")
