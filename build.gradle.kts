import org.gradle.api.tasks.Exec
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.sonarqube)
}

fun rootPath(path: String): String = layout.projectDirectory.dir(path).asFile.absolutePath
val sonarProjectVersion = providers.gradleProperty("sonarProjectVersion")
    .orElse(providers.gradleProperty("KANI_VERSION_NAME"))
    .orElse(providers.gradleProperty("KANJI_ANKI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANJI_ANKI_VERSION_NAME"))
    .orElse(libs.versions.appVersionName.get())

val sonarFullCoverage = providers.gradleProperty("sonarFullCoverage").map(String::toBoolean).getOrElse(false)
val sonarAppMainBinaries = providers.gradleProperty("sonarAppMainBinaries")
    .getOrElse(rootPath("app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"))
val sonarMainBinaries = listOf(
    rootPath("fsrs-java/build/classes/kotlin/main"),
    rootPath("core/build/classes/kotlin/main"),
    rootPath("domain/build/classes/kotlin/main"),
    rootPath("sync-domain/build/classes/kotlin/main"),
    rootPath("data-api/build/classes/kotlin/main"),
    rootPath("writing-core/build/classes/kotlin/main"),
    rootPath("dictionary-core/build/classes/kotlin/main"),
    rootPath("update-core/build/classes/kotlin/main"),
    rootPath("platform-contracts/build/classes/kotlin/main"),
    rootPath("desktop-app/build/classes/kotlin/main"),
    rootPath("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    sonarAppMainBinaries,
)
val sonarTestBinaries = listOf(
    rootPath("fsrs-java/build/classes/kotlin/test"),
    rootPath("core/build/classes/kotlin/test"),
    rootPath("core/build/classes/java/test"),
    rootPath("domain/build/classes/kotlin/test"),
    rootPath("sync-domain/build/classes/kotlin/test"),
    rootPath("data-api/build/classes/kotlin/test"),
    rootPath("data-api/build/classes/java/test"),
    rootPath("data-api/build/classes/kotlin/testFixtures"),
    rootPath("writing-core/build/classes/kotlin/test"),
    rootPath("dictionary-core/build/classes/kotlin/test"),
    rootPath("update-core/build/classes/kotlin/test"),
    rootPath("platform-contracts/build/classes/kotlin/test"),
    rootPath("desktop-app/build/classes/kotlin/test"),
    rootPath("app/build/intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"),
    rootPath("app/build/intermediates/built_in_kotlinc/debugAndroidTest/compileDebugAndroidTestKotlin/classes"),
    rootPath("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"),
)
val sonarCoveragePaths = buildList<String> {
    add(rootPath("fsrs-java/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("domain/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("sync-domain/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("data-api/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("writing-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("dictionary-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("update-core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("platform-contracts/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("desktop-app/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    if (sonarFullCoverage) {
        add(rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"))
    }
}

val sonarPreflight = tasks.register("sonarPreflight") {
    group = "verification"
    description = "Fails closed when deterministic Sonar bytecode or coverage inputs are missing."
    inputs.property("binaryPaths", (sonarMainBinaries + sonarTestBinaries).distinct())
    inputs.property("coveragePaths", sonarCoveragePaths)
    doLast {
        fun inputPaths(name: String): List<String> =
            (inputs.properties.getValue(name) as Iterable<*>).map { it.toString() }

        val missingBinaries = inputPaths("binaryPaths")
            .filterNot { path ->
                val input = java.io.File(path)
                input.isFile || (
                    input.isDirectory && input.walkTopDown().any { candidate ->
                        candidate.isFile && (candidate.extension == "class" || candidate.extension == "jar")
                    }
                )
            }
        val missingCoverage = inputPaths("coveragePaths").filterNot { path ->
            java.io.File(path).let { it.isFile && it.length() > 0L }
        }
        val missing = missingBinaries + missingCoverage
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing deterministic Sonar inputs; run ./gradlew ciQuality before sonar:\n" +
                    missing.joinToString("\n") { " - $it" },
            )
        }
    }
}
val fastSonarCoverageExclusions = listOf(
    "app/src/main/kotlin/dev/bee/kanjianki/MainActivity*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeChromeCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeFocusQueueCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeMetricsCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/HomeSyncConfirmDialogCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/ProgressAnalyticsCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/KaniBottomNavCompose.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/*View.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/SyncProgressPanel.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/anki/*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/data/HistoricalSyncStore.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStore*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/data/DictionaryStore.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/reminders/*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/reminders/ReminderReceiverDailyActions.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/sync/*.kt",
    "app/src/main/kotlin/dev/bee/kanjianki/widget/KaniWidget.kt",
)
val alwaysSonarCoverageExclusions = listOf(
    "**/src/test/**",
    "**/src/androidTest/**",
    "**/src/debug/**",
    "desktop-app/src/main/kotlin/dev/bee/kanjianki/desktop/DesktopFoundationWindow.kt",
)
val sonarCoverageExclusions = alwaysSonarCoverageExclusions + if (sonarFullCoverage) {
    emptyList()
} else {
    fastSonarCoverageExclusions
}

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
        property("sonar.projectVersion", sonarProjectVersion.get())
        property("sonar.java.binaries", sonarMainBinaries.joinToString(","))
        property("sonar.java.test.binaries", sonarTestBinaries.joinToString(","))
        property("sonar.coverage.jacoco.xmlReportPaths", sonarCoveragePaths.joinToString(","))
        property("sonar.coverage.exclusions", sonarCoverageExclusions.joinToString(","))
        property("sonar.scanner.skipJreProvisioning", "true")
        property("sonar.exclusions", "**/src/debug/**")
    }
}

tasks.named("sonar") {
    dependsOn(sonarPreflight)
}

tasks.register<Exec>("testDictionaryAssets") {
    group = "verification"
    description = "Runs deterministic Python tests for generated dictionary and similar-kanji assets."
    commandLine("python3", "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py")
}

tasks.register<Exec>("testRalphScripts") {
    group = "verification"
    description = "Runs deterministic Python tests for Ralph loop and screenshot scripts."
    commandLine("python3", "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_*.py")
}

tasks.register<Exec>("testCiScripts") {
    group = "verification"
    description = "Runs deterministic Python tests for CI scripts and live-fixture helpers."
    commandLine("python3", "-m", "unittest", "discover", "-s", "ci/tests", "-p", "test_*.py")
}

tasks.register<Exec>("generateDesktopIcons") {
    group = "build setup"
    description = "Generates committed PNG, ICO, and ICNS package icons from the canonical SVG."
    inputs.file(layout.projectDirectory.file("branding/kani-app-icon.svg"))
    inputs.file(layout.projectDirectory.file("tools/generate_desktop_icons.py"))
    outputs.files(
        layout.projectDirectory.file("desktop-app/src/main/packaging/icons/kani.png"),
        layout.projectDirectory.file("desktop-app/src/main/packaging/icons/kani.ico"),
        layout.projectDirectory.file("desktop-app/src/main/packaging/icons/kani.icns"),
        layout.projectDirectory.file(
            "desktop-app/src/main/packaging/icons/icon-manifest.json",
        ),
    )
    commandLine("python3", "tools/generate_desktop_icons.py", "--write")
}

tasks.register<Exec>("verifyDesktopIcons") {
    group = "verification"
    description = "Fails when a desktop package icon diverges from the canonical SVG."
    inputs.file(layout.projectDirectory.file("branding/kani-app-icon.svg"))
    inputs.file(layout.projectDirectory.file("tools/generate_desktop_icons.py"))
    inputs.files(
        layout.projectDirectory.file("desktop-app/src/main/packaging/icons/kani.png"),
        layout.projectDirectory.file("desktop-app/src/main/packaging/icons/kani.ico"),
        layout.projectDirectory.file("desktop-app/src/main/packaging/icons/kani.icns"),
        layout.projectDirectory.file(
            "desktop-app/src/main/packaging/icons/icon-manifest.json",
        ),
    )
    commandLine("python3", "tools/generate_desktop_icons.py", "--check")
}

tasks.register("testBuildLogic") {
    group = "verification"
    description = "Runs convention-plugin tests, including the Android library fixture."
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}

val desktopPythonExecutable = if (
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
) {
    "python"
} else {
    "python3"
}

tasks.register<Exec>("testDesktopTooling") {
    group = "verification"
    description = "Runs host-portable desktop gate, icon-contract, boundary, and smoke-runner tests."
    commandLine(
        desktopPythonExecutable,
        "-m",
        "unittest",
        "tools.test_desktop_ci_gates",
        "tools.test_desktop_ci_workflow",
        "tools.test_generate_desktop_icons",
        "tools.test_merge_verification_metadata",
        "tools.test_module_boundaries",
        "tools.test_run_desktop_installed_image_smoke",
    )
}

tasks.register<Exec>("testDesktopCiScripts") {
    group = "verification"
    description = "Runs host-portable desktop CI classifier and verification-metadata artifact tests."
    commandLine(
        desktopPythonExecutable,
        "-m",
        "unittest",
        "ci.tests.test_capture_verification_metadata",
        "ci.tests.test_classify_desktop_ci",
        "ci.tests.test_verification_metadata_artifact_validation",
    )
}

val desktopCiTasks = listOf(
    "testBuildLogic",
    ":fsrs-java:check",
    ":core:check",
    ":domain:check",
    ":sync-domain:check",
    ":data-api:check",
    ":writing-core:check",
    ":dictionary-core:check",
    ":update-core:check",
    ":platform-contracts:check",
    ":desktop-app:check",
    "testDesktopCiScripts",
    "testDesktopTooling",
)

tasks.register("ciDesktop") {
    group = "verification"
    description = "Runs deterministic desktop, shared JVM, build-logic, icon-contract, and tooling checks for the current host."
    dependsOn(desktopCiTasks)
}

val desktopInstalledImageDirectory =
    layout.projectDirectory.dir("desktop-app/build/compose/binaries/main/app")

val smokeDesktopInstalledImage = tasks.register<Exec>("smokeDesktopInstalledImage") {
    group = "verification"
    description = "Runs the current-host installed desktop image in isolated temporary-data smoke mode."
    dependsOn(":desktop-app:createDistributable")
    mustRunAfter(":desktop-app:packageDistributionForCurrentOS")
    inputs.file(
        layout.projectDirectory.file(
            "tools/run_desktop_installed_image_smoke.py",
        ),
    )
    inputs.dir(desktopInstalledImageDirectory)
    workingDir(layout.projectDirectory)
    commandLine(
        desktopPythonExecutable,
        "tools/run_desktop_installed_image_smoke.py",
        "--image-root",
        desktopInstalledImageDirectory.asFile.absolutePath,
    )
}

tasks.register("ciDesktopPackage") {
    group = "verification"
    description = "Builds the current-host desktop image and native package, then runs the installed-image smoke contract."
    dependsOn(
        ":desktop-app:packageDistributionForCurrentOS",
        smokeDesktopInstalledImage,
    )
}

val fastCiTasks = listOf(
    "testBuildLogic",
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
    ":data-api:test",
    ":data-api:jacocoTestReport",
    ":data-api:jacocoTestCoverageVerification",
    ":writing-core:test",
    ":writing-core:jacocoTestReport",
    ":writing-core:jacocoTestCoverageVerification",
    ":dictionary-core:test",
    ":dictionary-core:jacocoTestReport",
    ":dictionary-core:jacocoTestCoverageVerification",
    ":update-core:test",
    ":update-core:jacocoTestReport",
    ":update-core:jacocoTestCoverageVerification",
    ":platform-contracts:test",
    ":platform-contracts:jacocoTestReport",
    ":platform-contracts:jacocoTestCoverageVerification",
    ":app:compileDebugKotlin",
    ":app:testDebugUnitTest",
    ":app:jacocoDebugUnitTestReport",
    ":app:compileDebugAndroidTestKotlin",
    ":app:compileDebugAndroidTestJavaWithJavac",
    ":app:lintDebug",
    "testDictionaryAssets",
    "testRalphScripts",
    "testCiScripts",
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
        ":data-api:jar",
        ":writing-core:jar",
        ":dictionary-core:jar",
        ":update-core:jar",
        ":platform-contracts:jar",
        ":desktop-app:jacocoTestReport",
        ":desktop-app:jar",
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

tasks.register("ciAll") {
    group = "verification"
    description = "Aggregates Android, quality, desktop, and current-host desktop-package confidence gates without making cross-host claims."
    dependsOn(
        "ciQuality",
        "ciDesktop",
        "ciDesktopPackage",
    )
}
