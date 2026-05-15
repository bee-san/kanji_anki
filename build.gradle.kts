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
val sonarMainBinaries = listOf(
    rootPath("core/build/classes/java/main"),
    rootPath("core/build/classes/kotlin/main"),
    rootPath("app/build/classes/kotlin/debug"),
    rootPath("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    rootPath("app/build/intermediates/kotlin-classes/debug"),
)

val sonarTestBinaries = listOf(
    rootPath("core/build/classes/java/test"),
    rootPath("core/build/classes/kotlin/test"),
    rootPath("app/build/classes/kotlin/debugUnitTest"),
    rootPath("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
    rootPath("app/build/intermediates/kotlin-classes/debugUnitTest"),
    rootPath("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"),
    rootPath("app/build/intermediates/kotlin-classes/debugAndroidTest"),
)

val sonarCoveragePaths = listOf(
    rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"),
    rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"),
    rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"),
)

val maybeSonarMainBinaries = listOf(
    rootPath("core/build/classes/java/main"),
    rootPath("core/build/classes/kotlin/main"),
    rootPath("app/build/classes/kotlin/debug"),
    rootPath("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    rootPath("app/build/intermediates/javac/debug/classes"),
    rootPath("app/build/intermediates/kotlin-classes/debug")
)

val maybeSonarTestBinaries = listOf(
    rootPath("core/build/classes/java/test"),
    rootPath("core/build/classes/kotlin/test"),
    rootPath("app/build/classes/kotlin/debugUnitTest"),
    rootPath("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
    rootPath("app/build/intermediates/javac/debugUnitTest/classes"),
    rootPath("app/build/intermediates/kotlin-classes/debugUnitTest"),
    rootPath("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"),
    rootPath("app/build/intermediates/javac/debugAndroidTest/classes"),
    rootPath("app/build/intermediates/kotlin-classes/debugAndroidTest")
)

val maybeSonarCoveragePaths = listOf(
    rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"),
    rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"),
    rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"),
)

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
