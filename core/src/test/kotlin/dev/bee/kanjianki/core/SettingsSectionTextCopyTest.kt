package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("Sources, filters, and range.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Study settings", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("New cards, timing, workload, and ladder.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Reminders, sync, and updates.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Dictionaries, strokes, fonts, and credits.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Overview", SettingsSectionTextCopy.settingsCockpitLabel())
        assertEquals("Choose a section below.", SettingsSectionTextCopy.settingsHeroBody())
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Import filters", SettingsSectionTextCopy.importFiltersStatusLabel())
        assertEquals("Suspended card range", SettingsSectionTextCopy.importRanksStatusLabel())
        assertEquals("Reminder: Off", SettingsSectionTextCopy.statusPillDescription("Reminder", "Off"))
        assertEquals("Daily reminder", SettingsSectionTextCopy.reminderStatusLabel())
        assertEquals("Daily sync", SettingsSectionTextCopy.dailySyncStatusLabel())
        assertEquals("App updates", SettingsSectionTextCopy.updatesStatusLabel())
        assertEquals("Cards per kanji", SettingsSectionTextCopy.matchingCardsStatusLabel())
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
            assertEquals("ソース、フィルター、範囲を選ぶ。", SettingsSectionTextCopy.settingsAnkiSourceBody())
            assertEquals("学習設定", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
            assertEquals("新規カード、タイミング、負荷、ラダーを設定。", SettingsSectionTextCopy.settingsStudyBehaviorBody())
            assertEquals("自動化", SettingsSectionTextCopy.settingsAutomationTitle())
            assertEquals("リマインダー、同期、更新を管理。", SettingsSectionTextCopy.settingsAutomationBody())
            assertEquals("表示とデータ", SettingsSectionTextCopy.settingsReferenceDataTitle())
            assertEquals("辞書、ストローク、フォント、クレジットを管理。", SettingsSectionTextCopy.settingsReferenceDataBody())
            assertEquals("概要", SettingsSectionTextCopy.settingsCockpitLabel())
            assertEquals("下のセクションを選ぶ。", SettingsSectionTextCopy.settingsHeroBody())
            assertEquals("ノートタイプ", SettingsSectionTextCopy.noteTypeStatusLabel())
            assertEquals("インポートフィルター", SettingsSectionTextCopy.importFiltersStatusLabel())
            assertEquals("停止カードの範囲", SettingsSectionTextCopy.importRanksStatusLabel())
            assertEquals("毎日のリマインダー", SettingsSectionTextCopy.reminderStatusLabel())
            assertEquals("毎日の同期", SettingsSectionTextCopy.dailySyncStatusLabel())
            assertEquals("アプリ更新", SettingsSectionTextCopy.updatesStatusLabel())
            assertEquals("漢字ごとのカード数", SettingsSectionTextCopy.matchingCardsStatusLabel())
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
