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
    fun settingsCockpitLabel(): String = localizedText("Overview", "概要")

    @JvmStatic
    fun settingsHeroBody(): String = localizedText("Choose a section.", "下のセクションを選ぶ。")

    @JvmStatic
    fun timingDiagnosticsTitle(): String = localizedText("Timing diagnostics", "タイミング診断")

    @JvmStatic
    fun timingDiagnosticsBody(): String = localizedText(
        "Capture and export the latest cold Home → Study launch. Long-press the overview hero to reopen this screen.",
        "最新のコールドなホーム→学習起動を記録して出力します。概要ヒーローを長押しするとこの画面を開き直せます。",
    )

    @JvmStatic
    fun timingDiagnosticsReportTitle(): String = localizedText("Report preview", "レポートのプレビュー")

    @JvmStatic
    fun timingDiagnosticsPrewarmTitle(): String = localizedText("Prewarm study assets", "学習アセットを事前読み込み")

    @JvmStatic
    fun timingDiagnosticsPrewarmBody(): String = localizedText(
        "Warm the dictionary and stroke caches before the next run.",
        "次の計測の前に辞書とストロークのキャッシュを温めます。",
    )

    @JvmStatic
    fun timingDiagnosticsResetTitle(): String = localizedText("Reset capture", "記録をリセット")

    @JvmStatic
    fun timingDiagnosticsResetBody(): String = localizedText(
        "Clear the current timeline and start fresh.",
        "現在のタイムラインを消して新しく始めます。",
    )

    @JvmStatic
    fun timingDiagnosticsCopyLabel(): String = localizedText("Copy report", "レポートをコピー")

    @JvmStatic
    fun timingDiagnosticsResetLabel(): String = localizedText("Reset now", "今すぐリセット")

    @JvmStatic
    fun timingDiagnosticsPrewarmLabel(): String = localizedText("Prewarm now", "今すぐ事前読み込み")

    @JvmStatic
    fun timingDiagnosticsCopiedToast(): String = localizedText("Timing report copied.", "タイミングレポートをコピーしました。")

    @JvmStatic
    fun timingDiagnosticsResetToast(): String = localizedText("Timing capture cleared.", "計測を消去しました。")

    @JvmStatic
    fun timingDiagnosticsPrewarmToast(): String = localizedText("Study assets prewarmed.", "学習アセットを事前読み込みしました。")

    @JvmStatic
    fun noteTypeStatusLabel(): String = localizedText("Note type", "ノートタイプ")

    @JvmStatic
    fun importFiltersStatusLabel(): String = localizedText("Import filters", "インポートフィルター")

    @JvmStatic
    fun importRanksStatusLabel(): String = localizedText("Suspended card range", "停止カードの範囲")

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

    fun sectionOpenDescription(title: String): String = if (isJapaneseLocale()) "${title}を開く" else "Open $title"

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
