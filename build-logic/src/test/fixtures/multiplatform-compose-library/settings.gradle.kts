pluginManagement {
    providers.gradleProperty("kaniBuildLogicPath").orNull?.let { includeBuild(it) }
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

rootProject.name = "multiplatform-compose-library-conventions-fixture"
