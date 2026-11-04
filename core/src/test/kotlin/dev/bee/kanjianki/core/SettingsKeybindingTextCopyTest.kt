package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsKeybindingTextCopyTest {
    @Test
    fun keybindingStringsStayStable() {
        assertEquals("Keyboard shortcuts", SettingsKeybindingTextCopy.keybindingsTitle())
        assertEquals(
            "Remap the Study keys. Buttons keep working either way.",
            SettingsKeybindingTextCopy.keybindingsSummary(),
        )
        assertEquals("No key", SettingsKeybindingTextCopy.unboundLabel())
        assertEquals("Reset to defaults", SettingsKeybindingTextCopy.resetLabel())
        assertEquals("Add key", SettingsKeybindingTextCopy.addKeyLabel())
        assertEquals("Remove Ctrl+Z", SettingsKeybindingTextCopy.removeKeyLabel("Ctrl+Z"))
        assertEquals("Already Fail", SettingsKeybindingTextCopy.conflictReason("Fail"))
        assertEquals("Used by the system: Copy", SettingsKeybindingTextCopy.reservedReason("Copy"))
    }

    @Test
    fun commandsAreNamedTheWayTheStudyButtonsNameThem() {
        // The editor must not leak the wire id: a row labelled "grade_pass" tells the
        // user nothing about which button it moves.
        assertEquals("Show answer / continue", SettingsKeybindingTextCopy.commandLabel("primary"))
        assertEquals("Pass", SettingsKeybindingTextCopy.commandLabel("grade_pass"))
        assertEquals("Fail", SettingsKeybindingTextCopy.commandLabel("grade_fail"))
        assertEquals("Undo", SettingsKeybindingTextCopy.commandLabel("undo"))
        for (id in listOf("primary", "grade_pass", "grade_fail", "undo")) {
            assertNotEquals(id, SettingsKeybindingTextCopy.commandLabel(id))
        }
        // An id from a build with more commands falls back to the id rather than to a
        // neighbouring command's name, which would mislabel the row it edits.
        assertEquals("grade_hard", SettingsKeybindingTextCopy.commandLabel("grade_hard"))
    }

    @Test
    fun keybindingStringsTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("キーボードショートカット", SettingsKeybindingTextCopy.keybindingsTitle())
            assertEquals(
                "学習中のキーを変更できます。ボタンはそのまま使えます。",
                SettingsKeybindingTextCopy.keybindingsSummary(),
            )
            assertEquals("答えを表示・次へ", SettingsKeybindingTextCopy.commandLabel("primary"))
            assertEquals("正解", SettingsKeybindingTextCopy.commandLabel("grade_pass"))
            assertEquals("不正解", SettingsKeybindingTextCopy.commandLabel("grade_fail"))
            assertEquals("元に戻す", SettingsKeybindingTextCopy.commandLabel("undo"))
            assertEquals("キーなし", SettingsKeybindingTextCopy.unboundLabel())
            assertEquals("初期設定に戻す", SettingsKeybindingTextCopy.resetLabel())
            assertEquals("キーを追加", SettingsKeybindingTextCopy.addKeyLabel())
            assertEquals("Ctrl+Zを削除", SettingsKeybindingTextCopy.removeKeyLabel("Ctrl+Z"))
            assertEquals("正解が使用中", SettingsKeybindingTextCopy.conflictReason("正解"))
            // The OS action keeps its own name inside the translated sentence: it is the
            // word the platform itself shows, so translating it would hide the match.
            assertEquals("OSが使用中: Copy", SettingsKeybindingTextCopy.reservedReason("Copy"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
