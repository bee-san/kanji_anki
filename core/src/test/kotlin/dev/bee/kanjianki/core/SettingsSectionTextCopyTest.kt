package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("Set fields, filters, rank range, and sync.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Study settings", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("Set card order, timing, workload, and ladder.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Set reminders, sync, and update checks.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Review dictionaries, stroke data, fonts, and credits.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Overview", SettingsSectionTextCopy.settingsCockpitLabel())
        assertEquals("Pick a section to adjust.", SettingsSectionTextCopy.settingsHeroBody())
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel())
        assertEquals("Jiten rank range", SettingsSectionTextCopy.importRanksStatusLabel())
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"))
        assertEquals("Daily reminder", SettingsSectionTextCopy.reminderStatusLabel())
        assertEquals("Daily sync", SettingsSectionTextCopy.dailySyncStatusLabel())
        assertEquals("App updates", SettingsSectionTextCopy.updatesStatusLabel())
        assertEquals("Min cards per kanji", SettingsSectionTextCopy.matchingCardsStatusLabel())
        assertEquals("Collapse Study settings", SettingsSectionTextCopy.categoryToggleDescription(true, "Study settings"))
        assertEquals("Expand Automation", SettingsSectionTextCopy.categoryToggleDescription(false, "Automation"))
        assertEquals("1 card", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
    }

    @Test
    fun sectionLabelsTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("インポートと同期", SettingsSectionTextCopy.settingsAnkiSourceTitle())
            assertEquals("フィールド、フィルター、ランク範囲、同期を設定。", SettingsSectionTextCopy.settingsAnkiSourceBody())
            assertEquals("学習設定", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
            assertEquals("カード順、タイミング、負荷、ラダーを設定。", SettingsSectionTextCopy.settingsStudyBehaviorBody())
            assertEquals("自動化", SettingsSectionTextCopy.settingsAutomationTitle())
            assertEquals("リマインダー、同期、更新確認を設定。", SettingsSectionTextCopy.settingsAutomationBody())
            assertEquals("表示とデータ", SettingsSectionTextCopy.settingsReferenceDataTitle())
            assertEquals("辞書、ストロークデータ、フォント、クレジットを確認。", SettingsSectionTextCopy.settingsReferenceDataBody())
            assertEquals("概要", SettingsSectionTextCopy.settingsCockpitLabel())
            assertEquals("調整するセクションを選択。", SettingsSectionTextCopy.settingsHeroBody())
            assertEquals("ノートタイプ", SettingsSectionTextCopy.noteTypeStatusLabel())
            assertEquals("インポートフィルター", SettingsSectionTextCopy.importFiltersStatusLabel())
            assertEquals("Jitenランク範囲", SettingsSectionTextCopy.importRanksStatusLabel())
            assertEquals("毎日のリマインダー", SettingsSectionTextCopy.reminderStatusLabel())
            assertEquals("毎日の同期", SettingsSectionTextCopy.dailySyncStatusLabel())
            assertEquals("アプリ更新", SettingsSectionTextCopy.updatesStatusLabel())
            assertEquals("漢字ごとの最小カード数", SettingsSectionTextCopy.matchingCardsStatusLabel())
            assertEquals("毎日のリマインダー：Off", SettingsSectionTextCopy.statusPillDescription(SettingsSectionTextCopy.reminderStatusLabel(), "Off"))
            assertEquals("学習設定を折りたたむ", SettingsSectionTextCopy.categoryToggleDescription(true, SettingsSectionTextCopy.settingsStudyBehaviorTitle()))
            assertEquals("自動化を展開する", SettingsSectionTextCopy.categoryToggleDescription(false, SettingsSectionTextCopy.settingsAutomationTitle()))
            assertEquals("展開済み", SettingsSectionTextCopy.categoryStateDescription(true))
            assertEquals("折りたたみ済み", SettingsSectionTextCopy.categoryStateDescription(false))
            assertEquals("1枚", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
            assertEquals("2枚", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
