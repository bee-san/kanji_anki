package dev.bee.kanjianki.core

object SettingsSectionTextCopy {
    @JvmStatic
    fun settingsAnkiSourceTitle(): String = "Import from Anki"

    @JvmStatic
    fun settingsAnkiSourceBody(): String = "AnkiDroid note type, filters, and frequency."

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = "Study behavior"

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = "Learning steps, FSRS retention, workload, sorting, ahead limits, and ladder thresholds."

    @JvmStatic
    fun settingsAutomationTitle(): String = "Automation"

    @JvmStatic
    fun settingsAutomationBody(): String = "Daily sync, reminders, and update checks that run Kani in the background."

    @JvmStatic
    fun settingsReferenceDataTitle(): String = "Display & data"

    @JvmStatic
    fun settingsReferenceDataBody(): String = "Offline dictionaries, stroke data, fonts, and attribution."

    @JvmStatic
    fun settingsCockpitLabel(): String = "Settings cockpit"

    @JvmStatic
    fun settingsHeroBody(): String {
        return "Grouped by import, study, automation, and data."
    }

    @JvmStatic
    fun noteTypeStatusLabel(): String = "Note type"

    @JvmStatic
    fun importFiltersStatusLabel(): String = "Import filters"

    @JvmStatic
    fun importRanksStatusLabel(): String = "Import ranks"

    @JvmStatic
    fun reminderStatusLabel(): String = "Reminder"

    @JvmStatic
    fun dailySyncStatusLabel(): String = "Daily sync"

    @JvmStatic
    fun updatesStatusLabel(): String = "Updates"

    @JvmStatic
    fun matchingCardsStatusLabel(): String = "Matching cards"

    @JvmStatic
    fun statusPillDescription(label: String, value: String): String = "$label: $value"

    @JvmStatic
    fun categoryToggleDescription(expanded: Boolean, title: String): String {
        return (if (expanded) "Collapse " else "Expand ") + title
    }

    @JvmStatic
    fun settingsCategoryPanelCount(panels: Int): String = panels.toString() + if (panels == 1) " card" else " cards"
}
