package dev.bee.kanjianki.core

object SettingsSectionTextCopy {
    @JvmStatic
    fun settingsAnkiSourceTitle(): String = "Import & sync"

    @JvmStatic
    fun settingsAnkiSourceBody(): String = "Fields, filters, range, and sync."

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = "Study settings"

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = "New cards, timing, workload, and ladder controls."

    @JvmStatic
    fun settingsAutomationTitle(): String = "Automation"

    @JvmStatic
    fun settingsAutomationBody(): String = "Reminders and updates."

    @JvmStatic
    fun settingsReferenceDataTitle(): String = "Display & data"

    @JvmStatic
    fun settingsReferenceDataBody(): String = "Dictionaries, stroke data, fonts, and credits."

    @JvmStatic
    fun settingsCockpitLabel(): String = "Settings overview"

    @JvmStatic
    fun settingsHeroBody(): String {
        return "Choose a section."
    }

    @JvmStatic
    fun noteTypeStatusLabel(): String = "Note type"

    @JvmStatic
    fun importFiltersStatusLabel(): String = "Import filters"

    @JvmStatic
    fun importRanksStatusLabel(): String = "Suspended card range"

    @JvmStatic
    fun reminderStatusLabel(): String = "Daily reminder"

    @JvmStatic
    fun dailySyncStatusLabel(): String = "Daily sync"

    @JvmStatic
    fun updatesStatusLabel(): String = "App updates"

    @JvmStatic
    fun matchingCardsStatusLabel(): String = "Cards per kanji"

    @JvmStatic
    fun statusPillDescription(label: String, value: String): String = "$label: $value"

    @JvmStatic
    fun categoryToggleDescription(expanded: Boolean, title: String): String {
        return (if (expanded) "Collapse " else "Expand ") + title
    }

    @JvmStatic
    fun categoryStateDescription(expanded: Boolean): String = if (expanded) "Expanded" else "Collapsed"

    @JvmStatic
    fun settingsCategoryPanelCount(panels: Int): String = panels.toString() + if (panels == 1) " card" else " cards"
}
