package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLadderThresholdTextCopyTest {
    @Test
    fun ladderThresholdStringsStayStable() {
        assertEquals("Ladder movement", SettingsLadderThresholdTextCopy.ladderThresholdsTitle())
        assertEquals("Due reviews move cards. Repeats stay practice-only.", SettingsLadderThresholdTextCopy.ladderThresholdsBody())
        assertEquals("Days to move up", SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel())
        assertEquals("Fails to move down", SettingsLadderThresholdTextCopy.failsToGoDownLabel())
        assertEquals("Use default movement rules", SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel())
        assertEquals("Save rules", SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel())
        assertEquals("Movement rules saved.", SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast())
    }

    @Test
    fun ladderThresholdStringsTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("ラダー移動", SettingsLadderThresholdTextCopy.ladderThresholdsTitle())
            assertEquals("期限レビューでカードが移動する。繰り返しは練習のみ。", SettingsLadderThresholdTextCopy.ladderThresholdsBody())
            assertEquals("上がるまでの日数", SettingsLadderThresholdTextCopy.fsrsDaysToGoUpLabel())
            assertEquals("下がるまでの失敗数", SettingsLadderThresholdTextCopy.failsToGoDownLabel())
            assertEquals("既定の移動ルールを使う", SettingsLadderThresholdTextCopy.useDefaultLadderThresholdsLabel())
            assertEquals("ルールを保存", SettingsLadderThresholdTextCopy.saveLadderThresholdsLabel())
            assertEquals("移動ルールを保存しました。", SettingsLadderThresholdTextCopy.ladderThresholdsSavedToast())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
