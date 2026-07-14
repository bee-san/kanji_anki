package dev.bee.kanjianki.core

import java.util.Locale

object SettingsSectionTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun settingsTitle(): String = localizedText("Settings", "設定")

    @JvmStatic
    fun settingsAnkiSourceTitle(): String = localizedText("Import & sync", "インポートと同期")

    @JvmStatic
    fun settingsAnkiSourceBody(): String = localizedText("Choose sources, filters, and rank range.", "ソース、フィルター、範囲を選ぶ。")

    @JvmStatic
    fun settingsStudyBehaviorTitle(): String = localizedText("Study settings", "学習設定")

    @JvmStatic
    fun settingsStudyBehaviorBody(): String = localizedText("Set new cards, timing, workload, and ladder.", "新規カード、タイミング、負荷、ラダーを設定。")

    @JvmStatic
    fun settingsAutomationTitle(): String = localizedText("Automation", "自動化")

    @JvmStatic
    fun settingsAutomationBody(): String = localizedText("Manage reminders, sync, and updates.", "リマインダー、同期、更新を管理。")

    @JvmStatic
    fun settingsAppearanceTitle(): String = localizedText("Appearance", "外観")

    @JvmStatic
    fun settingsAppearanceBody(): String = localizedText("Choose your app theme.", "アプリのテーマを選ぶ。")

    @JvmStatic
    fun settingsReferenceDataTitle(): String = localizedText("Display & data", "表示とデータ")

    @JvmStatic
    fun settingsReferenceDataBody(): String = localizedText("Manage dictionaries, strokes, fonts, and credits.", "辞書、ストローク、フォント、クレジットを管理。")

    @JvmStatic
    fun noteTypeStatusLabel(): String = localizedText("Note type", "ノートタイプ")

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

    fun sectionOpenDescription(title: String): String = if (isJapaneseLocale()) "${title}を開く" else "Open $title"

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
