package dev.bee.kanjianki.buildlogic

object KaniDesktopIdentity {
    const val APPLICATION_NAME = "Kani"
    const val DESKTOP_ID = "dev.bee.kanjianki.desktop"
    const val MAIN_CLASS = "dev.bee.kanjianki.desktop.MainKt"
    const val WINDOWS_UPGRADE_UUID = "C972670E-BCCD-4D5E-9ACC-2C8877ABA799"
    const val DESCRIPTION = "Kanji study companion for Anki"
    const val VENDOR = "bee-san"
    const val ICON_DIRECTORY = "src/main/packaging/icons"
}

object KaniDesktopPackageVersions {
    /**
     * macOS jpackage rejects a zero leading component. Offset the semantic
     * major by one so the package version remains reversible and monotonic.
     */
    fun macOsJpackage(versionName: String): String {
        val version = KaniVersioning.parse(versionName)
        return "${version.major + 1}.${version.minor}.${version.patch}"
    }
}
