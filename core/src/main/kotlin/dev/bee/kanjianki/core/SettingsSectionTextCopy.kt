package dev.bee.kanjianki.core

object SettingsSectionTextCopy {
    @JvmStatic
    fun settingsAnkiSourceTitle(): String = "Import & sync"

    @JvmStatic
    fun settingsAnkiSourceBody(): String = "Choose which AnkiDroid cards Kani imports and when sync runs."

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = "Deck options"

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = "Learning steps, deck limits, retention, workload, sorting, and ladder movement."

    @JvmStatic
    fun settingsAutomationTitle(): String = "Reminders & updates"

    @JvmStatic
    fun settingsAutomationBody(): String = "Daily reminders, daily sync, and app updates."

    @JvmStatic
    fun settingsReferenceDataTitle(): String = "Display & data"

    @JvmStatic
    fun settingsReferenceDataBody(): String = "Offline dictionaries, stroke data, fonts, and attribution."

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
