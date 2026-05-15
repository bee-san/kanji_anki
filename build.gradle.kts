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

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
        property("sonar.projectVersion", sonarProjectVersion.get())
        property(
            "sonar.java.binaries",
            listOf(
                rootPath("core/build/classes/java/main"),
                rootPath("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
            ).joinToString(",")
        )
        property(
            "sonar.java.test.binaries",
            listOf(
                rootPath("core/build/classes/java/test"),
                rootPath("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
                rootPath("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"),
            ).joinToString(",")
        )
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            listOf(
                rootPath("core/build/reports/jacoco/test/jacocoTestReport.xml"),
                rootPath("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"),
                rootPath("app/build/reports/coverage/androidTest/debug/connected/report.xml"),
            ).joinToString(",")
        )
    }
}
