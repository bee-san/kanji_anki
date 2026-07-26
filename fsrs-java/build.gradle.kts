import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("kani.kotlin-library-conventions")
}

kotlin {
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    api(kotlin("stdlib"))
}
