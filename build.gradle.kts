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

val sonarJavaBinaries = listOf(
    "core/build/classes/java/main",
    "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
).map(::rootPath)

val sonarJavaTestBinaries = buildList {
    add(rootPath("core/build/classes/java/test"))
    add(rootPath("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"))
    if (file("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes").exists()) {
        add(rootPath("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"))
    }
}

val sonarJaCoCoReportPaths = buildList {
    add(rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"))
    add(rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    if (file("app/build/reports/coverage/androidTest/debug/connected/report.xml").exists()) {
        add(rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"))
    }
}

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
        property("sonar.projectVersion", sonarProjectVersion.get())
        property("sonar.java.binaries", sonarJavaBinaries.joinToString(","))
        property("sonar.java.test.binaries", sonarJavaTestBinaries.joinToString(","))
        property("sonar.coverage.jacoco.xmlReportPaths", sonarJaCoCoReportPaths.joinToString(","))
    }
}
