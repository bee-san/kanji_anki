plugins {
    id("kani.kotlin-library-conventions")
}

kaniLibrary {
    coverageExcludes.add("**/PlatformNeutralSyncEngine\$*\$1*.class")
    coverageExcludes.add("**/SyncCancellation\$DefaultImpls.class")
}

dependencies {
    api(project(":core"))
    api(project(":data-api"))
    api(project(":dictionary-core"))
    api(project(":platform-contracts"))
    api(project(":sync-api"))
    api(project(":sync-domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(testFixtures(project(":data-api")))
    testImplementation(testFixtures(project(":sync-api")))
    testImplementation(libs.kotlinx.coroutines.test)
}
