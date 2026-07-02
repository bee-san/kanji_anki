package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyAheadTextCopyTest {
    @Test
    fun studyAheadStringsStayStable() {
        assertEquals("Study ahead", SettingsStudyAheadTextCopy.studyAheadTitle())
        assertEquals("Review early; learning waits stay fixed. Early answers never move the ladder.", SettingsStudyAheadTextCopy.studyAheadBody())
        assertEquals("Save minutes", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
        assertEquals("Look-ahead saved.", SettingsStudyAheadTextCopy.studyAheadSavedToast())
        assertEquals("Minutes (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
        assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
        assertEquals("1440 minutes (24h)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
        assertEquals("Enter whole minutes (0-1440).", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
        assertEquals(
            "Enter 0-1440 minutes; 0 turns it off.",
            SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
        )
    }

    @Test
    fun studyAheadStringsTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("先取り学習", SettingsStudyAheadTextCopy.studyAheadTitle())
            assertEquals("早めに復習し、学習待ちはそのまま。早めの回答でラダーは動きません。", SettingsStudyAheadTextCopy.studyAheadBody())
            assertEquals("先取りを保存", SettingsStudyAheadTextCopy.saveStudyAheadLabel())
            assertEquals("先取り学習を保存しました。", SettingsStudyAheadTextCopy.studyAheadSavedToast())
            assertEquals("分 (0-1440)", SettingsStudyAheadTextCopy.studyAheadMinutesLabel())
            assertEquals("0-1440", SettingsStudyAheadTextCopy.studyAheadMinutesRange())
            assertEquals("1440 分 (24時間)", SettingsStudyAheadTextCopy.studyAheadMaxDescription())
            assertEquals("整数の分を入力してください (0-1440).", SettingsStudyAheadTextCopy.studyAheadWholeNumberErrorText())
            assertEquals(
                "0-1440 分を入力してください。0 でオフになります。",
                SettingsStudyAheadTextCopy.studyAheadOutOfRangeErrorText(),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
