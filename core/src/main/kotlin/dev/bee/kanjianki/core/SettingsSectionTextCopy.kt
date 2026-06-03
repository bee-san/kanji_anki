package dev.bee.kanjianki.core

object SettingsSectionTextCopy {
    @JvmStatic
    fun settingsAnkiSourceTitle(): String = "Anki import"

    @JvmStatic
    fun settingsAnkiSourceBody(): String = "Note type, import filters, and suspended-card range."

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = "Study behavior"

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = "Card ordering, daily limits, learning steps, review retention, study ahead, and ladder rules."

    @JvmStatic
    fun settingsAutomationTitle(): String = "Sync, reminders, and updates"

    @JvmStatic
    fun settingsAutomationBody(): String = "Daily sync, reminders, and app updates."

    @JvmStatic
    fun settingsReferenceDataTitle(): String = "Reference data"

    @JvmStatic
    fun settingsReferenceDataBody(): String = "Bundled dictionaries, stroke data, fonts, and licenses."

    @JvmStatic
    fun settingsCockpitLabel(): String = "Settings overview"

    @JvmStatic
    fun settingsHeroBody(): String {
        return "Grouped by area: Anki import, study behavior, sync, reminders, updates, and reference data."
    }

    @JvmStatic
    fun noteTypeStatusLabel(): String = "Anki note type"

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
    fun settingsCategoryPanelCount(panels: Int): String = panels.toString() + if (panels == 1) " card" else " cards"
}
