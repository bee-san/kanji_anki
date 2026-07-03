plugins {
    id("kani.kotlin-library-conventions")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api(kotlin("stdlib"))
}
