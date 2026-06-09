package dev.bee.kanjianki.core

import java.util.Locale

object SettingsSectionTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun settingsAnkiSourceTitle(): String = localizedText("Import & sync", "インポートと同期")

    @JvmStatic
    fun settingsAnkiSourceBody(): String = localizedText("Fields, filters, range, and sync.", "フィールド、フィルター、範囲、同期。")

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = localizedText("Study settings", "学習設定")

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = localizedText("Cards, timing, workload, and ladder.", "カード、タイミング、負荷、ラダー。")

    @JvmStatic
    fun settingsAutomationTitle(): String = localizedText("Automation", "自動化")

    @JvmStatic
    fun settingsAutomationBody(): String = localizedText("Reminders and updates.", "リマインダー、更新。")

    @JvmStatic
    fun settingsReferenceDataTitle(): String = localizedText("Display & data", "表示とデータ")

    @JvmStatic
    fun settingsReferenceDataBody(): String = localizedText("Dictionaries, stroke data, fonts, and credits.", "辞書、ストロークデータ、フォント、クレジット。")

    @JvmStatic
    fun settingsCockpitLabel(): String = localizedText("Overview", "概要")

    @JvmStatic
    fun settingsHeroBody(): String = localizedText("Choose a section.", "セクションを選択。")

    @JvmStatic
    fun noteTypeStatusLabel(): String = localizedText("Note type", "ノートタイプ")

    @JvmStatic
    fun importFiltersStatusLabel(): String = localizedText("Import filters", "インポートフィルター")

    @JvmStatic
    fun importRanksStatusLabel(): String = localizedText("Jiten rank range", "Jitenランク範囲")

    @JvmStatic
    fun reminderStatusLabel(): String = localizedText("Daily reminder", "毎日のリマインダー")

    @JvmStatic
    fun dailySyncStatusLabel(): String = localizedText("Daily sync", "毎日の同期")

    @JvmStatic
    fun updatesStatusLabel(): String = localizedText("App updates", "アプリ更新")

    @JvmStatic
    fun matchingCardsStatusLabel(): String = localizedText("Cards per kanji", "漢字ごとのカード数")

    @JvmStatic
    fun statusPillDescription(label: String, value: String): String =
        if (isJapaneseLocale()) "$label：$value" else "$label: $value"

    @JvmStatic
    fun categoryToggleDescription(expanded: Boolean, title: String): String {
        return if (isJapaneseLocale()) {
            title + if (expanded) "を折りたたむ" else "を展開する"
        } else {
            (if (expanded) "Collapse " else "Expand ") + title
        }
    }

    @JvmStatic
    fun categoryStateDescription(expanded: Boolean): String =
        if (isJapaneseLocale()) if (expanded) "展開済み" else "折りたたみ済み" else if (expanded) "Expanded" else "Collapsed"

    @JvmStatic
    fun settingsCategoryPanelCount(panels: Int): String =
        if (isJapaneseLocale()) {
            "${panels}枚"
        } else {
            panels.toString() + if (panels == 1) " card" else " cards"
        }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
