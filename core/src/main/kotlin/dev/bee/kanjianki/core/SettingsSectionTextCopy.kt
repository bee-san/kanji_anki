package dev.bee.kanjianki.core

object SettingsSectionTextCopy {
    @JvmStatic
    fun settingsAnkiSourceTitle(): String = "Import & sync"

    @JvmStatic
    fun settingsAnkiSourceBody(): String = "AnkiDroid note fields, import filters, frequency range, and daily sync live together."

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = "Deck options"

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = "Study steps, deck limits, FSRS retention, workload, sorting, ahead limits, and ladder thresholds."

    @JvmStatic
    fun settingsAutomationTitle(): String = "Automation"

    @JvmStatic
    fun settingsAutomationBody(): String = "Daily reminders and update checks that run in the background."

    @JvmStatic
    fun settingsReferenceDataTitle(): String = "Display & data"

    @JvmStatic
    fun settingsReferenceDataBody(): String = "Offline dictionaries, stroke data, fonts, and attribution shown by the app."

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
