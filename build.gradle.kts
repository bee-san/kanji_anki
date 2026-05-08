plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.sonarqube") version "7.3.0.8198"
}

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
        property(
            "sonar.java.binaries",
            listOf(
                "core/build/classes/java/main",
                "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
            ).joinToString(",")
        )
        property(
            "sonar.java.test.binaries",
            listOf(
                "core/build/classes/java/test",
                "app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
                "app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes",
            ).joinToString(",")
        )
    }
}
