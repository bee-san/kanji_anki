package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsSectionTextCopyTest {
    @Test
    fun sectionLabelsPreserveFormatting() {
        assertEquals("Settings", SettingsSectionTextCopy.settingsTitle())
        assertEquals("Import & sync", SettingsSectionTextCopy.settingsAnkiSourceTitle())
        assertEquals("Choose sources, filters, and rank range.", SettingsSectionTextCopy.settingsAnkiSourceBody())
        assertEquals("Study settings", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
        assertEquals("Set new cards, timing, workload, and ladder.", SettingsSectionTextCopy.settingsStudyBehaviorBody())
        assertEquals("Automation", SettingsSectionTextCopy.settingsAutomationTitle())
        assertEquals("Manage reminders, sync, and updates.", SettingsSectionTextCopy.settingsAutomationBody())
        assertEquals("Display & data", SettingsSectionTextCopy.settingsReferenceDataTitle())
        assertEquals("Manage dictionaries, strokes, fonts, and credits.", SettingsSectionTextCopy.settingsReferenceDataBody())
        assertEquals("Note type", SettingsSectionTextCopy.noteTypeStatusLabel())
        assertEquals("Collapse Study settings", SettingsSectionTextCopy.categoryToggleDescription(true, "Study settings"))
        assertEquals("Expand Automation", SettingsSectionTextCopy.categoryToggleDescription(false, "Automation"))
        assertEquals("1 card", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
        assertEquals("2 cards", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
        assertEquals("Open Study settings", SettingsSectionTextCopy.sectionOpenDescription("Study settings"))
        assertEquals("Open Study settings", SettingsTextCopy.sectionOpenDescription("Study settings"))
    }

    @Test
    fun sectionLabelsTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("設定", SettingsSectionTextCopy.settingsTitle())
            assertEquals("インポートと同期", SettingsSectionTextCopy.settingsAnkiSourceTitle())
            assertEquals("ソース、フィルター、範囲を選ぶ。", SettingsSectionTextCopy.settingsAnkiSourceBody())
            assertEquals("学習設定", SettingsSectionTextCopy.settingsStudyBehaviorTitle())
            assertEquals("新規カード、タイミング、負荷、ラダーを設定。", SettingsSectionTextCopy.settingsStudyBehaviorBody())
            assertEquals("自動化", SettingsSectionTextCopy.settingsAutomationTitle())
            assertEquals("リマインダー、同期、更新を管理。", SettingsSectionTextCopy.settingsAutomationBody())
            assertEquals("表示とデータ", SettingsSectionTextCopy.settingsReferenceDataTitle())
            assertEquals("辞書、ストローク、フォント、クレジットを管理。", SettingsSectionTextCopy.settingsReferenceDataBody())
            assertEquals("ノートタイプ", SettingsSectionTextCopy.noteTypeStatusLabel())
            assertEquals("学習設定を折りたたむ", SettingsSectionTextCopy.categoryToggleDescription(true, SettingsSectionTextCopy.settingsStudyBehaviorTitle()))
            assertEquals("自動化を展開する", SettingsSectionTextCopy.categoryToggleDescription(false, SettingsSectionTextCopy.settingsAutomationTitle()))
            assertEquals("展開済み", SettingsSectionTextCopy.categoryStateDescription(true))
            assertEquals("折りたたみ済み", SettingsSectionTextCopy.categoryStateDescription(false))
            assertEquals("1枚", SettingsSectionTextCopy.settingsCategoryPanelCount(1))
            assertEquals("2枚", SettingsSectionTextCopy.settingsCategoryPanelCount(2))
            assertEquals("学習設定を開く", SettingsSectionTextCopy.sectionOpenDescription(SettingsSectionTextCopy.settingsStudyBehaviorTitle()))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
