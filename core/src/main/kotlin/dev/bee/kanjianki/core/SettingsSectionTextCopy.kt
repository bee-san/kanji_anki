package dev.bee.kanjianki.core

object SettingsSectionTextCopy {
    @JvmStatic
    fun settingsAnkiSourceTitle(): String = "Import & sync"

    @JvmStatic
    fun settingsAnkiSourceBody(): String = "Choose which AnkiDroid cards Kani imports and when sync runs."

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = "Study behavior"

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = "New-card order, workload, retention, learning steps, study ahead, and ladder movement."

    @JvmStatic
    fun settingsAutomationTitle(): String = "Reminders & updates"

    @JvmStatic
    fun settingsAutomationBody(): String = "Daily reminders, daily sync, and app updates."

    @JvmStatic
    fun settingsReferenceDataTitle(): String = "Offline data & credits"

    @JvmStatic
    fun settingsReferenceDataBody(): String = "Kanji frequency range, dictionaries, stroke data, fonts, and attribution."

    @JvmStatic
    fun settingsCockpitLabel(): String = "Settings overview"

    @JvmStatic
    fun settingsHeroBody(): String {
        return "Choose a section below. Expanding it keeps the page in place and preserves your scroll position."
    }

    @JvmStatic
    fun noteTypeStatusLabel(): String = "Note type"

    @JvmStatic
    fun importFiltersStatusLabel(): String = "Import filters"

    @JvmStatic
    fun importRanksStatusLabel(): String = "Kanji frequency range"

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
    fun settingsCategoryPanelCount(panels: Int): String = panels.toString() + if (panels == 1) " setting" else " settings"
}
