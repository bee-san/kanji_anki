package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StudyTextCopyLocaleLabelsTest {
    @Test
    fun studyCopyUsesEnglishLabelsByDefault() {
        assertEquals("Meaning", StudyTextCopy.meaningLabel())
        assertEquals("Pass", StudyTextCopy.passLabel())
        assertEquals("Fail", StudyTextCopy.failLabel())
        assertEquals("New cards", StudyTextCopy.newCardsLabel())
        assertEquals("Back home", StudyTextCopy.backHomeLabel())
        assertEquals("Close study", StudyTextCopy.closeStudyLabel())
        assertEquals("Study progress", StudyTextCopy.studyProgressDescription())
        assertEquals("Study", StudyTextCopy.studyLabel())
        assertEquals("Practice", StudyTextCopy.practiceLabel())
    }

    @Test
    fun studyCopyTranslatesLabelsToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("意味", StudyTextCopy.meaningLabel())
            assertEquals("合格", StudyTextCopy.passLabel())
            assertEquals("不合格", StudyTextCopy.failLabel())
            assertEquals("新規カード", StudyTextCopy.newCardsLabel())
            assertEquals("ホームに戻る", StudyTextCopy.backHomeLabel())
            assertEquals("学習を閉じる", StudyTextCopy.closeStudyLabel())
            assertEquals("学習進捗", StudyTextCopy.studyProgressDescription())
            assertEquals("学習", StudyTextCopy.studyLabel())
            assertEquals("練習", StudyTextCopy.practiceLabel())
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
