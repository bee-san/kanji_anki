plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.sonarqube") version "7.3.0.8198"
}

fun rootPath(path: String): String = layout.projectDirectory.dir(path).asFile.absolutePath

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
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
            ).joinToString(",")
        )
    }
}
