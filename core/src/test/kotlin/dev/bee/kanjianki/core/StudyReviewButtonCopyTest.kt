package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StudyReviewButtonCopyTest {
    @Test
    fun reviewButtonsUsePassFailLabelsPerStudyUiSpec() {
        assertEquals("Reveal", StudyReviewButtonCopy.revealLabel())
        assertEquals("Fail", StudyReviewButtonCopy.againLabel())
        assertEquals("Pass", StudyReviewButtonCopy.goodLabel())
        assertEquals(StudyTextCopy.failLabel(), StudyReviewButtonCopy.againLabel())
        assertEquals(StudyTextCopy.passLabel(), StudyReviewButtonCopy.goodLabel())
    }

    @Test
    fun reviewButtonDescriptionsExplainSchedulingEffect() {
        assertEquals("Fail: show this card again sooner", StudyReviewButtonCopy.againContentDescription())
        assertEquals("Pass: keep the next review on schedule", StudyReviewButtonCopy.goodContentDescription())
    }

    @Test
    fun reviewButtonsTranslateToJapaneseLocale() {
        withJapaneseLocale {
            assertEquals("答えを見る", StudyReviewButtonCopy.revealLabel())
            assertEquals("不合格", StudyReviewButtonCopy.againLabel())
            assertEquals("合格", StudyReviewButtonCopy.goodLabel())
            assertEquals(StudyTextCopy.failLabel(), StudyReviewButtonCopy.againLabel())
            assertEquals(StudyTextCopy.passLabel(), StudyReviewButtonCopy.goodLabel())
            assertEquals("不合格: このカードを早めに再表示する", StudyReviewButtonCopy.againContentDescription())
            assertEquals("合格: 次回の復習を予定どおりに保つ", StudyReviewButtonCopy.goodContentDescription())
            assertEquals("元に戻す", StudyReviewButtonCopy.undoLabel())
        }
    }

    @Test
    fun undoLabelUsesPlainUndoTextByDefault() {
        assertEquals("Undo", StudyReviewButtonCopy.undoLabel())
    }

    private fun withJapaneseLocale(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
