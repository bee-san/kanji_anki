plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.sonarqube") version "7.3.0.8198"
}

fun rootPath(path: String): String = layout.projectDirectory.dir(path).asFile.absolutePath
val sonarProjectVersion = providers.gradleProperty("KANI_VERSION_NAME")
    .orElse(providers.gradleProperty("KANJI_ANKI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANI_VERSION_NAME"))
    .orElse(providers.environmentVariable("KANJI_ANKI_VERSION_NAME"))
    .orElse("0.4.33")

val sonarFullCoverage = providers.gradleProperty("sonarFullCoverage").map(String::toBoolean).getOrElse(false)
val maybeSonarMainBinaries = listOf(
    rootPath("core/build/classes"),
    rootPath("app/build/intermediates/javac"),
)
val maybeSonarTestBinaries = listOf(
    rootPath("core/build/classes"),
    rootPath("app/build/intermediates/javac"),
)
val maybeSonarCoveragePaths = buildList<String> {
    add(rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    if (sonarFullCoverage) {
        add(rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"))
    }
}

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
        property("sonar.projectVersion", sonarProjectVersion.get())
        property("sonar.java.binaries", maybeSonarMainBinaries.joinToString(","))
        property("sonar.java.test.binaries", maybeSonarTestBinaries.joinToString(","))
        property("sonar.coverage.jacoco.xmlReportPaths", maybeSonarCoveragePaths.joinToString(","))
    }
}
