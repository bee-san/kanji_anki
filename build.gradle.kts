plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.sonarqube") version "7.3.0.8198"
}

sonar {
    properties {
        property("sonar.projectKey", "bee-san_kanji_anki")
        property("sonar.organization", "bee-san")
    }
}
