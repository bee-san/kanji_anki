import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

val composeCompilerProbe by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.desktop.linux.x64)
    implementation(libs.compose.navigation)
    implementation(libs.skiko)

    add(composeCompilerProbe.name, libs.kotlin.compose.gradle.plugin)
}

fun Configuration.resolvedModuleCoordinates(): Set<String> {
    return incoming.resolutionResult.allComponents.mapNotNull { component ->
        val identifier = component.id as? ModuleComponentIdentifier
            ?: return@mapNotNull null
        "${identifier.group}:${identifier.module}:${identifier.version}"
    }.toSet()
}

val runtimeClasspath = configurations.named("runtimeClasspath")

tasks.register("printComposeJvmResolution") {
    group = "verification"
    description = "Prints the resolved Compose JVM compiler and runtime graph."

    doLast {
        val resolved = runtimeClasspath.get().resolvedModuleCoordinates() +
            composeCompilerProbe.resolvedModuleCoordinates()
        resolved.asSequence()
            .filter { coordinate ->
                coordinate.startsWith("org.jetbrains.compose.") ||
                    coordinate.startsWith("org.jetbrains.androidx.navigation:") ||
                    coordinate.startsWith("org.jetbrains.skiko:") ||
                    coordinate.startsWith("org.jetbrains.kotlin:compose-compiler-gradle-plugin:")
            }
            .sorted()
            .forEach { coordinate ->
                println("compose-jvm-resolution=$coordinate")
            }
    }
}
