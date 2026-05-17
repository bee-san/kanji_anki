import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    id("org.sonarqube") version "7.3.0.8198"
}

fun rootPath(path: String): String = layout.projectDirectory.dir(path).asFile.absolutePath
fun jvmMainBinaries(module: String) = listOf(
    rootPath("$module/build/classes/java/main"),
    rootPath("$module/build/classes/kotlin/main"),
)
fun jvmTestBinaries(module: String) = listOf(
    rootPath("$module/build/classes/java/test"),
    rootPath("$module/build/classes/kotlin/test"),
)
fun androidMainBinaries(module: String) = listOf(
    rootPath("$module/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    rootPath("$module/build/tmp/kotlin-classes/debug"),
)
fun androidTestBinaries(module: String) = listOf(
    rootPath("$module/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
    rootPath("$module/build/tmp/kotlin-classes/debugUnitTest"),
)
val sonarProjectVersion = providers.gradleProperty("sonarProjectVersion")
    .orElse(providers.gradleProperty("KANI_VERSION_NAME"))
    .orElse(providers.gradleProperty("KANJI_ANKI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANJI_ANKI_VERSION_NAME"))
    .orElse("0.4.33")

val sonarFullCoverage = providers.gradleProperty("sonarFullCoverage").map(String::toBoolean).getOrElse(false)
val jvmModules = listOf(
    "fsrs",
    "domain",
    "dictionary-core",
    "writing-core",
    "fsrs-java",
    "core",
)
val androidModules = listOf(
    "designsystem",
    "data",
    "ankidroid",
    "dictionary-android",
    "writing-android",
    "app",
)
val maybeSonarMainBinaries = jvmModules.flatMap(::jvmMainBinaries) +
    androidModules.flatMap(::androidMainBinaries)
val maybeSonarTestBinaries = jvmModules.flatMap(::jvmTestBinaries) +
    androidModules.flatMap(::androidTestBinaries)
val maybeSonarCoveragePaths = buildList<String> {
    add(rootPath("fsrs/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("domain/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("dictionary-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("writing-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("fsrs-java/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("data/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    add(rootPath("ankidroid/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    add(rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    if (sonarFullCoverage) {
        add(rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"))
    }
}
val fastSonarCoverageExclusions = listOf(
    "app/src/main/java/dev/bee/kanjianki/MainActivity*.java",
    "app/src/main/java/dev/bee/kanjianki/*View.java",
    "app/src/main/java/dev/bee/kanjianki/SyncProgressPanel.java",
    "app/src/main/java/dev/bee/kanjianki/anki/*.java",
    "app/src/main/java/dev/bee/kanjianki/data/HistoricalSyncStore.java",
    "app/src/main/java/dev/bee/kanjianki/data/LocalStore*.java",
    "app/src/main/java/dev/bee/kanjianki/data/SettingsRepository.java",
    "app/src/main/java/dev/bee/kanjianki/reminders/*.java",
    "app/src/main/java/dev/bee/kanjianki/sync/*.java",
)
val testSonarCoverageExclusions = listOf(
    "**/src/test/**",
    "**/src/androidTest/**",
)
val sonarCoverageExclusions = testSonarCoverageExclusions + if (sonarFullCoverage) {
    emptyList()
} else {
    fastSonarCoverageExclusions
}

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
        property("sonar.projectVersion", sonarProjectVersion.get())
        property("sonar.java.binaries", maybeSonarMainBinaries.joinToString(","))
        property("sonar.java.test.binaries", maybeSonarTestBinaries.joinToString(","))
        property("sonar.coverage.jacoco.xmlReportPaths", maybeSonarCoveragePaths.joinToString(","))
        property("sonar.coverage.exclusions", sonarCoverageExclusions.joinToString(","))
    }
}

tasks.register<Exec>("testDictionaryAssets") {
    group = "verification"
    description = "Runs deterministic Python tests for generated dictionary and similar-kanji assets."
    commandLine("python3", "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py")
}

val fastCiTasks = listOf(
    ":fsrs:test",
    ":fsrs:jacocoTestReport",
    ":domain:test",
    ":domain:jacocoTestReport",
    ":dictionary-core:test",
    ":dictionary-core:jacocoTestReport",
    ":writing-core:test",
    ":writing-core:jacocoTestReport",
    ":designsystem:compileDebugKotlin",
    ":data:compileDebugKotlin",
    ":data:testDebugUnitTest",
    ":data:jacocoDebugUnitTestReport",
    ":data:compileDebugAndroidTestKotlin",
    ":ankidroid:compileDebugKotlin",
    ":ankidroid:testDebugUnitTest",
    ":ankidroid:jacocoDebugUnitTestReport",
    ":dictionary-android:compileDebugKotlin",
    ":writing-android:compileDebugKotlin",
    ":fsrs-java:test",
    ":fsrs-java:jacocoTestReport",
    ":fsrs-java:jacocoTestCoverageVerification",
    ":core:test",
    ":core:jacocoTestReport",
    ":core:jacocoTestCoverageVerification",
    ":app:testDebugUnitTest",
    ":app:jacocoDebugUnitTestReport",
    ":app:compileDebugAndroidTestJavaWithJavac",
    ":app:lintDebug",
    "testDictionaryAssets",
)

tasks.register("ciFast") {
    group = "verification"
    description = "Runs the deterministic PR confidence gate: JVM tests, coverage, app unit tests, androidTest compile, lint, and asset tests."
    dependsOn(fastCiTasks)
}

tasks.register("ciQuality") {
    group = "verification"
    description = "Builds the deterministic test, coverage, and bytecode inputs used by SonarQube."
    dependsOn(
        "ciFast",
        ":fsrs-java:jar",
        ":core:jar",
        ":app:compileDebugJavaWithJavac",
    )
}

tasks.register("ciRelease") {
    group = "verification"
    description = "Runs the release confidence gate and assembles the signed release APK."
    dependsOn(
        "ciFast",
        ":app:assembleRelease",
    )
}
