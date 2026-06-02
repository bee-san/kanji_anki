import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.sonarqube") version "7.3.0.8198"
}

fun rootPath(path: String): String = layout.projectDirectory.dir(path).asFile.absolutePath
val sonarProjectVersion = providers.gradleProperty("sonarProjectVersion")
    .orElse(providers.gradleProperty("KANI_VERSION_NAME"))
    .orElse(providers.gradleProperty("KANJI_ANKI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANJI_ANKI_VERSION_NAME"))
    .orElse("0.4.33")

val sonarFullCoverage = providers.gradleProperty("sonarFullCoverage").map(String::toBoolean).getOrElse(false)
val maybeSonarMainBinaries = listOf(
    rootPath("fsrs-java/build/classes"),
    rootPath("core/build/classes"),
    rootPath("domain/build/classes"),
    rootPath("sync-domain/build/classes"),
    rootPath("writing-core/build/classes"),
    rootPath("dictionary-core/build/classes"),
    rootPath("update-core/build/classes"),
    rootPath("app/build/intermediates/javac"),
    rootPath("app/build/tmp/kotlin-classes/debug"),
    rootPath("app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
)
val maybeSonarTestBinaries = listOf(
    rootPath("fsrs-java/build/classes"),
    rootPath("core/build/classes"),
    rootPath("domain/build/classes"),
    rootPath("sync-domain/build/classes"),
    rootPath("writing-core/build/classes"),
    rootPath("dictionary-core/build/classes"),
    rootPath("update-core/build/classes"),
    rootPath("app/build/intermediates/javac"),
    rootPath("app/build/intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"),
    rootPath("app/build/intermediates/built_in_kotlinc/debugAndroidTest/compileDebugAndroidTestKotlin/classes"),
)
fun existingSonarPaths(paths: Iterable<String>): String = paths
    .filter { file(it).exists() }
    .joinToString(",")

val maybeSonarCoveragePaths = buildList<String> {
    add(rootPath("fsrs-java/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("domain/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("sync-domain/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("writing-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("dictionary-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("update-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    if (sonarFullCoverage) {
        add(rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"))
    }
}
val fastSonarCoverageExclusions = listOf(
    "app/src/main/java/dev/bee/kanjianki/MainActivity*.java",
    "app/src/main/kotlin/dev/bee/kanjianki/MainActivity*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeChromeCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeFocusQueueCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeMetricsCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeSyncConfirmDialogCompose.kt",
    "app/src/main/java/dev/bee/kanjianki/*View.java",
    "app/src/main/kotlin/dev/bee/kanjianki/*View.kt",
    "app/src/main/java/dev/bee/kanjianki/SyncProgressPanel.java",
    "app/src/main/kotlin/dev/bee/kanjianki/SyncProgressPanel.kt",
    "app/src/main/java/dev/bee/kanjianki/anki/*.java",
    "app/src/main/kotlin/dev/bee/kanjianki/anki/*.kt",
    "app/src/main/java/dev/bee/kanjianki/data/HistoricalSyncStore.java",
    "app/src/main/kotlin/dev/bee/kanjianki/data/HistoricalSyncStore.kt",
    "app/src/main/java/dev/bee/kanjianki/data/LocalStore*.java",
    "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStore*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStore.kt",
    "app/src/main/java/dev/bee/kanjianki/data/SettingsRepository.java",
    "app/src/main/kotlin/dev/bee/kanjianki/data/SettingsRepository.kt",
    "app/src/main/java/dev/bee/kanjianki/reminders/*.java",
    "app/src/main/kotlin/dev/bee/kanjianki/reminders/*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/reminders/ReminderReceiverDailyActions.kt",
    "app/src/main/java/dev/bee/kanjianki/sync/*.java",
    "app/src/main/kotlin/dev/bee/kanjianki/sync/*.kt",
)
val testSonarCoverageExclusions = listOf(
    "**/src/test/**",
    "**/src/androidTest/**",
    "**/src/debug/**",
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
        property("sonar.java.binaries", existingSonarPaths(maybeSonarMainBinaries))
        property("sonar.java.test.binaries", existingSonarPaths(maybeSonarTestBinaries))
        property("sonar.coverage.jacoco.xmlReportPaths", existingSonarPaths(maybeSonarCoveragePaths))
        property("sonar.coverage.exclusions", sonarCoverageExclusions.joinToString(","))
        property("sonar.exclusions", "**/src/debug/**")
    }
}

tasks.register<Exec>("testDictionaryAssets") {
    group = "verification"
    description = "Runs deterministic Python tests for generated dictionary and similar-kanji assets."
    commandLine("python3", "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py")
}

val fastCiTasks = listOf(
    ":fsrs-java:test",
    ":fsrs-java:jacocoTestReport",
    ":fsrs-java:jacocoTestCoverageVerification",
    ":core:test",
    ":core:jacocoTestReport",
    ":core:jacocoTestCoverageVerification",
    ":domain:test",
    ":domain:jacocoTestReport",
    ":domain:jacocoTestCoverageVerification",
    ":sync-domain:test",
    ":sync-domain:jacocoTestReport",
    ":sync-domain:jacocoTestCoverageVerification",
    ":writing-core:test",
    ":writing-core:jacocoTestReport",
    ":writing-core:jacocoTestCoverageVerification",
    ":dictionary-core:test",
    ":dictionary-core:jacocoTestReport",
    ":dictionary-core:jacocoTestCoverageVerification",
    ":update-core:test",
    ":update-core:jacocoTestReport",
    ":update-core:jacocoTestCoverageVerification",
    ":app:compileDebugKotlin",
    ":app:testDebugUnitTest",
    ":app:jacocoDebugUnitTestReport",
    ":app:compileDebugAndroidTestKotlin",
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
        ":domain:jar",
        ":sync-domain:jar",
        ":writing-core:jar",
        ":dictionary-core:jar",
        ":update-core:jar",
        ":app:compileDebugKotlin",
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
